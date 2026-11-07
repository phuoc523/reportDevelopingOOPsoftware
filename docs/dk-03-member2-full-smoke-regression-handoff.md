# DK-03 Member 2 Full Smoke & Regression Handoff

## 1. Objective

Validate the complete isolated Member 2 Docker stack from a clean volume through API, database, tenant-isolation, restart-persistence, reset, log, and regression-test gates before integration relay. No feature, architecture, or infrastructure change was required.

## 2. Git / Branch

- Branch: `feature/maintenance`
- HEAD at validation: `3ddd61880d6262703350f9de30fead979dfd1b0c`
- No commit, push, merge, or rebase was performed.
- The working tree already contained DB-01/02/03 and DK-01/02 work before DK-03. Those changes were preserved.

## 3. Files inspected

- `compose.member2.yml`
- `.env.example`
- Catalog and Operation Dockerfiles, application configuration, Flyway migrations, entities, repositories, controllers, DTOs, services, integration adapters, and tests relevant to this acceptance run
- `docs/dk-01-catalog-operation-docker-runtime-handoff.md`
- `docs/dk-02-member2-docker-compose-handoff.md`
- Compose-rendered configuration, container state, application logs, and both MySQL schemas

## 4. Files changed

- `docs/dk-03-member2-full-smoke-regression-handoff.md` — added by DK-03.

No application source, Dockerfile, Compose file, migration, test, or reusable script was changed by DK-03 because the acceptance run found no product defect. Other dirty-worktree entries are pre-existing work and were not reverted or claimed by this task.

## 5. Environment versions

- Docker Engine Client/Server: `29.6.2`
- Docker Compose: `v5.3.1`
- Host Java: Oracle Java `21.0.10`
- Maven: `3.9.15`, running with Eclipse Adoptium Java `21.0.11`
- Application image Java: OpenJDK `21.0.9` LTS
- Database image: MySQL `8.0.41`

Host patch/vendor differences did not affect acceptance; both build and container runtimes remain Java 21.

## 6. Compose topology

`docker compose -p scentstation-member2 -f compose.member2.yml config --quiet` passed. The rendered project contains exactly three services:

1. `mysql` on the dedicated Member 2 network and named data volume
2. `catalog-service`, connected only to `scentstation_catalog` as `catalog_app`
3. `operation-service`, connected only to `scentstation_operation` as `operation_app`, with the isolated-development profile `docker-local`

MySQL health is the application startup dependency. No unexpected service or cross-service database URL was present.

## 7. Clean startup result

Starting from `down -v --remove-orphans`, `up -d --build` completed successfully. MySQL became healthy; Catalog and Operation started without a crash loop; `/actuator/health` returned `UP` on both services. Operation explicitly ran with `docker-local`, as required only for the isolated Member 2 topology.

## 8. Container status

| Service | State | Health | Published port |
| --- | --- | --- | --- |
| `mysql` | Up | healthy | internal `3306`, `33060` |
| `catalog-service` | Up | application health `UP` | `8082:8082` |
| `operation-service` | Up | application health `UP` | `8085:8085` |

## 9. Runtime user verification

- Catalog: `uid=999(appuser) gid=999(appuser)` — non-root.
- Operation: `uid=999(appuser) gid=999(appuser)` — non-root.

## 10. Java runtime verification

Both application containers report OpenJDK `21.0.9` LTS. Java-major-version acceptance passed.

## 11. Catalog Flyway result

- `scentstation_catalog.flyway_schema_history` exists.
- V1 `initial catalog schema` has `success=1`.
- The schema contains exactly `flyway_schema_history` and `products` after fresh bootstrap.
- Restart logs reported schema version 1 and no migration necessary.

## 12. Operation Flyway result

- `scentstation_operation.flyway_schema_history` exists.
- V1 `initial operation schema` has `success=1`.
- The schema contains the expected 15 business tables plus `flyway_schema_history` (16 total).
- Restart logs reported schema version 1 and no migration necessary.

## 13. Hibernate validation result

Both services started after Flyway and initialized Hibernate successfully against the migrated MySQL schemas. Runtime configuration uses schema validation; no `ddl-auto=create`, `ddl-auto=update`, destructive recreation, or schema-mismatch error appeared in container startup logs.

## 14. DB privilege isolation

PASS.

- `catalog_app` can query `scentstation_catalog` and is denied access to `scentstation_operation`.
- `operation_app` can query `scentstation_operation` and is denied access to `scentstation_catalog`.
- Catalog datasource: `jdbc:mysql://mysql:3306/scentstation_catalog` only.
- Operation datasource: `jdbc:mysql://mysql:3306/scentstation_operation` only.

## 15. Catalog API smoke

Using the current public API contract:

- Created Tenant A product: ID `1`.
- Created Tenant B product: ID `2`.
- Listed Tenant A: exactly one Tenant A product returned.
- Read Tenant A product ID `1`: success.
- Updated Tenant A product price to `19.99`: persisted.
- Updated Tenant A product availability to `false`: persisted.

## 16. Catalog tenant isolation

Tenant B attempted to read Tenant A product ID `1`; the API returned HTTP `404`. Tenant A listing returned no Tenant B data. No cross-tenant product data leaked.

## 17. Catalog DB persistence

Direct verification after API smoke found:

- Product `1`: tenant `1001`, price `19.99`, availability `false`, creation/update timestamps populated.
- Product `2`: tenant `2001`, price `18.00`, availability `true`, creation/update timestamps populated.

Product `1` and its updated price remained present after full restart, application-only restart, stop/start, and down/up without `-v`.

## 18. Inventory smoke

Using the current Operation APIs under `docker-local`:

- Created batch ID `1` for Catalog product ID `1`.
- Created bottle IDs `1` and `2`.
- Installed bottle `1`; installation ID `1` was created.
- Adjusted bottle `1` quantity to `90.000`; API/DB state persisted.
- Removed bottle `1`; its state became available and the installation became inactive.
- Installed bottle `2` into the released slot; installation ID `2` was created and active.

## 19. Active installation smoke

PASS against DB-03 semantics:

- Duplicate active installation for the same bottle was rejected with HTTP `400`.
- A different bottle in the already occupied active slot was rejected with HTTP `409`.
- Removal retained historical installation ID `1` with `active=false` and a removal timestamp.
- The released slot was reusable by bottle `2`, producing active installation ID `2`.

## 20. Maintenance smoke

Using valid public state transitions:

- Created maintenance ticket ID `1` for Tenant A.
- Assigned technician actor `7001`; status became `ASSIGNED`.
- Acknowledged the assignment; status became `IN_PROGRESS`.
- Created and completed the required checklist item.
- Recorded a repair with result `SUCCESS`.
- Requested a post-maintenance test; status became `REQUESTED`.
- The `docker-local` Machine adapter supplied the isolated-development acknowledgement; submitting the result completed the post-test with status `PASSED` and an external test ID.

No business rule was bypassed.

## 21. Operation tenant isolation

- Tenant B read of Tenant A bottle ID `1` returned HTTP `404`.
- Tenant B read of Tenant A maintenance ticket ID `1` returned HTTP `404`.
- Tenant B assignment mutation against that ticket returned HTTP `404`.

Representative inventory and maintenance read/write isolation passed with no data leakage.

## 22. Internal FK regression

PASS. An isolated insert for tenant `2001` attempting to reference tenant `1001` inventory batch ID `1` was rejected by the database tenant-scoped internal foreign key. No invalid row was retained.

## 23. No-sample-data verification

Before smoke records were created, `maintenance_tickets` contained `0` rows. After the final clean-volume bootstrap, `products=0`, `bottles=0`, and `maintenance_tickets=0`. The maintenance sample initializer remained off under `docker-local`.

## 24. Restart persistence result

Full restart of MySQL, Catalog, and Operation passed. Both health endpoints returned `UP`; Catalog product `1`/price `19.99`, inventory bottle `1`/quantity `90.000`, and maintenance ticket `1`/status `IN_PROGRESS` persisted. Flyway did not recreate the schemas.

## 25. Application-only restart result

Restarting only Catalog and Operation passed. Health returned `UP`; representative product and maintenance data persisted; no duplicate migration or schema recreation occurred.

## 26. stop/start result

`docker compose ... stop` followed by `docker compose ... start` passed. MySQL returned healthy, both applications returned `UP`, and representative product/ticket data remained available without rebuild or reset.

## 27. down/up persistence result

`down` followed by `up -d` without `-v` passed. The named volume retained Catalog product, inventory bottle, and maintenance ticket data. Both applications returned `UP`.

## 28. down -v clean reset result

`down -v --remove-orphans` followed by `up -d --build` passed. Smoke-created business data was gone; Flyway rebuilt both empty schemas at V1; Catalog and Operation returned `UP`; Catalog had 2 total tables and Operation had 16; `maintenance_tickets=0`.

## 29. Log review

Blockers:

- None.

Non-blocking:

- None attributable to application or Compose behavior.

Expected:

- Three MySQL image warnings: deprecated `skip-host-cache`, a self-signed CA certificate, and the PID-file location warning.
- Framework/Test JVM warnings about Mockito dynamic agent attachment during Maven regression, which did not affect results.
- No application `ERROR`, unhandled exception, schema mismatch, Flyway failure, or connection retry storm was found in the reviewed Compose lifecycle logs.

## 30. Secret leak review

PASS. The actual three password values supplied via the test environment produced zero matches in the reviewed Compose/application logs. They are not included in this handoff or the DK-03 diff.

## 31. Catalog regression

- Command: `mvn clean test`
- Tests: `13`
- Failures: `0`
- Errors: `0`
- Skipped: `0`
- BUILD: `SUCCESS`

## 32. Operation regression

- Command: `mvn clean test`
- Tests: `265`
- Failures: `0`
- Errors: `0`
- Skipped: `0`
- BUILD: `SUCCESS`

## 33. Root Compose modified?

NO. DK-03 did not modify root `docker-compose.yml`.

## 34. Other services modified?

NO. DK-03 did not modify Identity, Gateway, Order, Machine, Reporting, Frontend, or Device. Pre-existing working-tree changes were left untouched.

## 35. Remaining Member 2 gaps

No remaining isolated Member 2 blocker was found. The only real integration boundary is intentional: a full-team topology must connect Operation to the real Machine service and must not enable `docker-local`.

Concise developer runbook:

1. Copy `.env.example` to `.env` and set the three Member 2 database passwords.
2. Run `docker compose -p scentstation-member2 -f compose.member2.yml up -d --build`.
3. Run `docker compose -p scentstation-member2 -f compose.member2.yml ps`.
4. Check `http://localhost:8082/actuator/health` and `http://localhost:8085/actuator/health` for `UP`.
5. Review with `docker compose -p scentstation-member2 -f compose.member2.yml logs`.
6. Stop while preserving data with `docker compose -p scentstation-member2 -f compose.member2.yml down`.
7. Reset completely with `docker compose -p scentstation-member2 -f compose.member2.yml down -v --remove-orphans`.

## 36. Integration relay readiness

Member 2 ready to hand off: **YES**.

All acceptance gates passed: valid three-service Compose, clean bootstrap, health, non-root Java 21 runtime, Flyway/Hibernate validation, DB-user isolation, Catalog/Inventory/Maintenance smoke, tenant isolation, internal FK enforcement, persistence lifecycle, clean reset, regressions, and scope protection.

## 37. Recommended next step

Prepare the Member 2 consolidated/integration handoff using the verified DK-01/DK-02/DK-03 artifacts. Do not merge automatically. The future integration Compose must provide the real Machine integration and must not enable `docker-local`.

## 38. Final status

DK-03 = **DONE**
