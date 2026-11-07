# DB-03 Operation Persistence Foundation Handoff

## 1. Objective

Establish a versioned MySQL persistence foundation for all 15 Operation tables, prove the active-installation uniqueness model on real MySQL, and enforce tenant-safe internal relationships without changing Inventory or Maintenance business workflows.

## 2. Git / Branch

- Branch: `feature/maintenance`
- Starting/current HEAD: `3ddd61880d6262703350f9de30fead979dfd1b0c`
- No commit, push, merge, rebase, or checkout was performed.
- Pre-existing worktree changes, including completed Catalog DB-02 work, were preserved and not modified by DB-03.

## 3. Files inspected

- Entire Operation source/test file inventory under `src/main/java`, `src/main/resources`, and `src/test`
- All 15 JPA entities and repositories
- Inventory installation, consumption, adjustment, reconciliation, refill, movement, batch, bottle, audit, and estimation workflows
- Maintenance ticket, assignment, checklist, repair, alert, post-test, audit, history, and sample-data workflows
- Existing controller, service, repository, integration, rollback, idempotency, tenant-isolation, and end-to-end tests
- `services/operation-service/pom.xml`
- `services/operation-service/Dockerfile`
- Catalog DB-02 Testcontainers setup read-only for consistency
- Current call paths and persistence symbols through CodeGraph

## 4. Files changed

Production:

- No production Java class or business method changed.

Config:

- `services/operation-service/pom.xml`
- `services/operation-service/src/main/resources/application.yml`
- `services/operation-service/src/test/resources/application.yml`
- `services/operation-service/src/test/resources/docker-java.properties`

Migration:

- `services/operation-service/src/main/resources/db/migration/V1__initial_operation_schema.sql`

Tests:

- `services/operation-service/src/test/java/com/scentstation/operation/OperationMySqlIntegrationTest.java`

Docs:

- `docs/db-03-operation-persistence-foundation-handoff.md`

No Catalog or other service file was modified for DB-03. The Dockerfile was inspected and left unchanged.

## 5. Operation database ownership

Operation owns all 15 migrated tables: eight Inventory tables and seven Maintenance tables. Internal Operation relationships use database foreign keys. Cross-service identifiers remain scalar values with no local parent requirement.

## 6. Inventory physical schema

1. `inventory_batches` — tenant/product batch identity, quantity, received/expiry dates, timestamps; unique tenant batch code.
2. `bottles` — tenant/product/batch references, bottle code, quantities, calibration, status, timestamps; unique tenant bottle code.
3. `bottle_installations` — bottle/machine/slot lifecycle, actors, measured quantities, nullable active marker.
4. `dispense_consumptions` — idempotent command result, bottle and machine/slot references, quantities, processing timestamp.
5. `bottle_movements` — immutable bottle movement history and optional machine/slot/actor context.
6. `inventory_adjustments` — bottle quantity before/after, actor, reason, timestamp.
7. `sensor_reconciliations` — idempotent sensor event, prediction/measurement/discrepancy, classification and timestamps.
8. `inventory_audit_events` — polymorphic Inventory audit history; `entity_id` intentionally has no generic FK.

All field types, lengths, nullability, decimal precision/scale, dates, timestamps, and current unique rules match the current entities.

## 7. Maintenance physical schema

1. `maintenance_tickets` — machine/slot/location/bottle scalar context, description, classification, assignment, lifecycle and timestamps.
2. `maintenance_assignment_history` — ticket assignment/reassignment/acknowledgement evidence.
3. `maintenance_checklist_items` — ticket checklist requirement/completion state and completion evidence.
4. `maintenance_repair_logs` — diagnosis, corrective action, optional part/cost/evidence and repair result.
5. `post_maintenance_tests` — request/result lifecycle and nullable external test ID.
6. `maintenance_alert_links` — idempotent critical-alert-to-ticket link.
7. `maintenance_audit_events` — Maintenance audit history; `entity_id` remains polymorphic and has no generic FK.

## 8. Flyway configuration

Spring Boot dependency management resolves:

- `org.flywaydb:flyway-core:10.20.1`
- `org.flywaydb:flyway-mysql:10.20.1` with runtime scope

Normal runtime uses standard `classpath:db/migration` discovery. Liquibase was not added.

## 9. V1 migration

`V1__initial_operation_schema.sql` is the single baseline migration for this unreleased service. It creates all 15 tables, constraints, tenant-safe internal keys, and query-driven indexes. Flyway applied it successfully to an empty MySQL 8.0.41 database before Hibernate validation.

## 10. Internal FK design

Every internal FK includes the child tenant and references a parent candidate key `(tenant_id, id)`. Every FK uses `ON UPDATE RESTRICT ON DELETE RESTRICT`; history, audit, consumption, and maintenance evidence cannot be erased by cascading parent deletion.

| Child | Parent | Tenant-safe strategy | Delete behavior |
|---|---|---|---|
| `bottles(tenant_id,batch_id)` | `inventory_batches(tenant_id,id)` | Composite FK | RESTRICT |
| `bottle_installations(tenant_id,bottle_id)` | `bottles(tenant_id,id)` | Composite FK | RESTRICT |
| `dispense_consumptions(tenant_id,bottle_id)` | `bottles(tenant_id,id)` | Composite FK | RESTRICT |
| `bottle_movements(tenant_id,bottle_id)` | `bottles(tenant_id,id)` | Composite FK | RESTRICT |
| `inventory_adjustments(tenant_id,bottle_id)` | `bottles(tenant_id,id)` | Composite FK | RESTRICT |
| `sensor_reconciliations(tenant_id,bottle_id)` | `bottles(tenant_id,id)` | Composite FK | RESTRICT |
| `maintenance_assignment_history(tenant_id,ticket_id)` | `maintenance_tickets(tenant_id,id)` | Composite FK | RESTRICT |
| `maintenance_checklist_items(tenant_id,ticket_id)` | `maintenance_tickets(tenant_id,id)` | Composite FK | RESTRICT |
| `maintenance_repair_logs(tenant_id,ticket_id)` | `maintenance_tickets(tenant_id,id)` | Composite FK | RESTRICT |
| `post_maintenance_tests(tenant_id,ticket_id)` | `maintenance_tickets(tenant_id,id)` | Composite FK | RESTRICT |
| `maintenance_alert_links(tenant_id,ticket_id)` | `maintenance_tickets(tenant_id,id)` | Composite FK | RESTRICT |
| `maintenance_audit_events(tenant_id,ticket_id)` | `maintenance_tickets(tenant_id,id)` | Nullable composite FK | RESTRICT |

`maintenance_tickets.bottle_id` intentionally has no FK. Current maintenance and critical-alert workflows can create a ticket independently of an Inventory bottle lifecycle, so enforcing this optional link would regress valid behavior.

## 11. Cross-service scalar references

There are no foreign keys for `tenant_id`, `product_id`, `machine_id`, `slot_id`, `location_id`, actor IDs, `command_id`, `external_test_id`, or `source_alert_id` to another service database. The MySQL test persists arbitrary product/machine/actor IDs without local parent rows.

No FK is attached to polymorphic Inventory or Maintenance `entity_id` columns.

## 12. Active installation design

The schema preserves the current model:

- Active row: `active = TRUE`
- Removed/history row: `active = NULL`
- Unique slot guard: `(tenant_id, machine_id, slot_id, active)`
- Unique bottle guard: `(tenant_id, bottle_id, active)`

MySQL unique indexes reject a second `TRUE` row because all indexed values are non-null and equal. MySQL permits multiple rows containing `NULL`, so removed rows for the same slot and bottle coexist. The real-MySQL test creates an active installation, rejects duplicate slot and bottle rows, removes it to `NULL`, reuses the slot, and preserves three historical `NULL` rows including repeated use of the original bottle.

## 13. Unique/idempotency constraints

Preserved and proven on MySQL:

- `inventory_batches(tenant_id,batch_code)`
- `bottles(tenant_id,bottle_code)`
- Active installation slot and bottle rules
- `dispense_consumptions(tenant_id,command_id)`
- `sensor_reconciliations(tenant_id,sensor_event_id)`
- `post_maintenance_tests(tenant_id,external_test_id)`
- `maintenance_alert_links(tenant_id,source_alert_id)`

The post-test constraint accepts multiple `NULL` external IDs and rejects duplicate non-null values within a tenant.

## 14. Index design

Indexes follow repository query shapes:

- Batches: tenant/product
- Bottles: tenant/batch, tenant/product, tenant/status
- Installations: tenant/bottle/time and tenant/machine/time; unique indexes serve active checks
- Dispense: tenant/bottle/time and tenant/machine/time
- Movements: tenant/time, tenant/bottle/time, tenant/machine/time, tenant/type/time
- Adjustments: tenant/bottle/time and tenant/time
- Reconciliations: tenant/bottle/time and tenant/machine/time
- Inventory audit: tenant/time, tenant/entity/time, tenant/action/time
- Tickets: tenant/status, tenant/machine, tenant/assignee, tenant/title
- Assignment: tenant/ticket/time and tenant/technician/time
- Checklist: tenant/ticket/id and incomplete-required lookup
- Repair, post-test, alert, and maintenance audit: tenant/ticket/order columns

Indexes already supplied by unique constraints were not duplicated without a distinct ordering/query need.

## 15. Enum/check constraints

Persisted enums remain `EnumType.STRING`. V1 uses bounded `VARCHAR` columns and intentionally does not add enum `CHECK` constraints. Inventory audit/action and maintenance workflow vocabularies are expected to evolve; avoiding duplicated enum lists prevents every enum addition from requiring an emergency constraint rewrite. Hibernate validation on MySQL accepts the mappings.

Lifecycle checks that could reject legitimate intermediate states were also omitted. Existing transactional workflow tests remain authoritative.

## 16. Quantity/money constraints

Database checks enforce:

- Received batch quantity greater than zero
- Valid batch date ordering
- Positive initial bottle quantity
- Non-negative current bottle quantity
- Positive calibration when present
- Non-negative installation measurements
- Positive consumed quantity and non-negative before/after snapshots
- Non-negative movement and adjustment snapshots
- Non-negative reconciliation quantities, discrepancy, and estimated sprays
- Non-negative repair cost when present

`DECIMAL` precision is preserved throughout; no monetary or quantity field was converted to floating point. Reconciliation discrepancy is non-negative because current source computes its absolute value.

## 17. Timestamp policy

`LocalDate` maps to `DATE`; `LocalDateTime` maps to `DATETIME(6)`. Existing JPA lifecycle callbacks remain responsible for timestamps. No database timezone conversion was introduced.

Remaining gap: the service still has no explicit application/database timezone contract; `LocalDateTime.now()` follows the application host clock.

## 18. Hibernate runtime policy

Normal MySQL runtime now uses:

- `spring.jpa.hibernate.ddl-auto=validate`
- `spring.jpa.open-in-view=false`

Flyway is the schema authority. Hibernate neither creates nor updates runtime MySQL tables.

## 19. H2 test strategy

Fast regression tests keep H2 in MySQL compatibility mode with:

- Flyway disabled
- `ddl-auto=create-drop`
- Open Session in View disabled

This avoids forcing MySQL migration syntax into every controller/unit/integration test while the dedicated Testcontainers suite provides MySQL acceptance.

## 20. MySQL Testcontainers strategy

- Dependencies: `org.testcontainers:junit-jupiter:1.20.6` and `org.testcontainers:mysql:1.20.6`
- Pinned image: `mysql:8.0.41`
- Dynamic datasource credentials from the container
- Flyway enabled and Hibernate `ddl-auto=validate`
- `docker-java.properties` pins API `1.44` for compatibility with the local Docker Engine 29 environment
- `disabledWithoutDocker=true` keeps fast tests usable without Docker; all recorded acceptance runs had Docker available and skipped zero tests

## 21. MySQL tests added

Six tests prove:

1. Flyway V1 success, all 15 tables present, and all 15 tables writable.
2. Missing-parent and cross-tenant batch/bottle, inventory child/bottle, and maintenance child/ticket links are rejected.
3. Cross-service scalar IDs remain unrestricted.
4. Active installation TRUE/NULL uniqueness and history behavior works on MySQL.
5. All important unique/idempotency rules, including nullable external test IDs, work.
6. Invalid quantity/money checks and transaction rollback work on MySQL.

Explicit command result:

- `mvn -Dtest=OperationMySqlIntegrationTest test`
- Tests: 6; Failures: 0; Errors: 0; Skipped: 0; BUILD SUCCESS

## 22. Inventory regression result

Tests: 121
Failures: 0
Errors: 0
Skipped: 0

## 23. Maintenance regression result

Tests: 137
Failures: 0
Errors: 0
Skipped: 0

## 24. Full mvn test

Tests: 265
Failures: 0
Errors: 0
Skipped: 0
BUILD: SUCCESS

The pre-change baseline was 259 tests; DB-03 adds six MySQL acceptance tests.

## 25. Full mvn clean test

Tests: 265
Failures: 0
Errors: 0
Skipped: 0
BUILD: SUCCESS

## 26. DB-01 P0 resolution

P0-1 versioned schema:
**RESOLVED** — Flyway V1 owns all 15 tables.

P0-2 active installation MySQL behavior:
**RESOLVED** — duplicate active slot/bottle rejection and multiple historical NULL rows are proven on MySQL 8.0.41.

P0-3 tenant-safe internal FK:
**RESOLVED** — 12 composite tenant-safe FKs reject orphan and cross-tenant internal links.

## 27. Remaining P1/P2 gaps

- Concurrent requests can still pass an application pre-check and lose at the database constraint. The database remains correct, but generic data-access exceptions are not globally mapped to domain-specific HTTP 409 responses.
- Existing unmanaged databases require an explicit Flyway baseline/adoption procedure before enabling this V1; V1 targets an empty database.
- Tenant identity is still supplied by current API contracts rather than a trusted authenticated tenant context.
- Application/database timezone policy remains implicit.
- No enum database constraint exists by design; application enums are the vocabulary authority.
- Pagination and retention/archival policies for large history/audit tables remain future work.

## 28. Flyway readiness

**READY.** Dependency resolution, migration discovery, V1 application, all-table writes, and Hibernate validation succeeded on MySQL 8.0.41.

## 29. Docker readiness

**READY WITH FIXES.** The persistence acceptance suite runs on Docker Engine 29 with Docker API 1.44 pinned for Testcontainers 1.20.6 compatibility. Docker Compose is not part of DB-03 and was not implemented.

## 30. DK-01 readiness

Can Docker review begin: **YES**.

DB-03 does not start or implement DK-01.

## 31. Final recommendation

DB-03 = **DONE**.

Proceed to DK-01 only as a separate scoped task. Preserve Flyway as schema authority, composite tenant-safe internal keys, scalar cross-service references, and the active TRUE/NULL uniqueness model.
