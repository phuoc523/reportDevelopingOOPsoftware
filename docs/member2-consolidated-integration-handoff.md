# Member 2 Consolidated Integration Handoff

## 1. Scope

- Catalog: `services/catalog-service/**`
- Operation: `services/operation-service/**`, including Inventory and Maintenance
- Isolated Member 2 runtime: `compose.member2.yml` and the Member 2 variables added to `.env.example`
- Supporting DB/Docker handoffs: DB-01 through DB-03 and DK-01 through DK-03

This task reviewed relay readiness only. It did not change production code, merge branches, or modify another member's service.

## 2. Current status

- DB-01: DONE
- DB-02: DONE
- DB-03: DONE
- DK-01: DONE
- DK-02: DONE
- DK-03: DONE

Current branch: `feature/maintenance` at `3ddd61880d6262703350f9de30fead979dfd1b0c`. It is aligned with `origin/feature/maintenance` (`0` ahead, `0` behind). Relative to the currently available `origin/main` metadata, main has 3 unique commits and this branch has 25; the integrator must use the selected integration baseline rather than assume a fast-forward.

## 3. Member 2 files/change groups

**A. Member 2 production changes**

- Catalog product controller/service/repository/entity changes implement tenant-scoped persistence and API behavior; Catalog runtime configuration uses its owned datasource and schema validation.
- Operation branch changes contain the Inventory and Maintenance controllers, services, persistence model, validation, audit/history workflows, and their public API contracts.
- Operation Machine boundary files include `MachineSlotClient`, `PostMaintenanceMachineClient`, their DTO/usage paths, and the explicitly local simulator implementations.

**B. Member 2 database/migration changes**

- `services/catalog-service/src/main/resources/db/migration/V1__initial_catalog_schema.sql`
- `services/operation-service/src/main/resources/db/migration/V1__initial_operation_schema.sql`
- Both service POM/config changes enabling Flyway, MySQL, and `ddl-auto: validate`.

**C. Member 2 Docker/Compose changes**

- Both service `Dockerfile` and `.dockerignore` files.
- `compose.member2.yml`.
- `.env.example` additions: `MYSQL_ROOT_PASSWORD`, `CATALOG_DB_PASSWORD`, `OPERATION_DB_PASSWORD`.

**D. Member 2 tests**

- Catalog controller, MySQL/Flyway, and tenant-isolation tests and test configuration.
- Operation Inventory/Maintenance unit and integration suites, MySQL acceptance test, and test configuration.

**E. Member 2 docs**

- `docs/db-01-catalog-operation-database-audit.md`
- `docs/db-02-catalog-persistence-foundation-handoff.md`
- `docs/db-03-operation-persistence-foundation-handoff.md`
- `docs/dk-01-catalog-operation-docker-runtime-handoff.md`
- `docs/dk-02-member2-docker-compose-handoff.md`
- `docs/dk-03-member2-full-smoke-regression-handoff.md`
- This consolidated handoff.

**F. Pre-existing/unrelated working-tree changes**

Root `.gitignore`, removed documentation placeholder files, general project documents, and files outside the Member 2 scope are not claimed as Member 2 relay changes. They were not reverted or modified by this task.

## 4. Runtime contract

**Catalog**

- Port: `8082`, configurable by `CATALOG_SERVICE_PORT`.
- Database: `scentstation_catalog`.
- Datasource variables: `CATALOG_DB_URL`, `CATALOG_DB_USERNAME`, `CATALOG_DB_PASSWORD`.
- Default local URL: `jdbc:mysql://localhost:3306/scentstation_catalog`.
- Health: `/actuator/health`.
- Runtime: Java 21, non-root container user.

**Operation**

- Port: `8085`, configurable by `OPERATION_SERVICE_PORT`.
- Database: `scentstation_operation`.
- Datasource variables: `OPERATION_DB_URL`, `OPERATION_DB_USERNAME`, `OPERATION_DB_PASSWORD`.
- Default local URL: `jdbc:mysql://localhost:3306/scentstation_operation`.
- Optional Spring profile selection: `SPRING_PROFILES_ACTIVE`; `docker-local` is valid only for isolated Member 2 development.
- Health: `/actuator/health`.
- Runtime: Java 21, non-root container user.

## 5. Database ownership

- Catalog exclusively owns `scentstation_catalog`; isolated Compose user: `catalog_app`.
- Operation exclusively owns `scentstation_operation`; isolated Compose user: `operation_app`.
- Operation stores external identifiers such as product/machine IDs as scalar references; there is no cross-service foreign key.
- Neither service may directly query the other's database. Full-team orchestration must preserve separate credentials and database grants.

## 6. Integration boundaries

- Catalog exposes tenant-scoped product REST APIs under `/api/products`; callers must supply the current tenant contract.
- Operation exposes Inventory and Maintenance REST APIs and owns their persistence.
- `MachineSlotClient#getSlot(tenantId, machineId, slotId)` is the Inventory boundary to the Machine service. Full-team integration must provide a real non-local implementation that returns tenant-correct slot state, including availability and maintenance-mode information.
- `PostMaintenanceMachineClient#requestPostMaintenanceTest(tenantId, machineId, ticketId)` is the Maintenance boundary to the Machine service. Full-team integration must provide a real non-local implementation that requests the test and returns the external tracking ID.
- No real Machine integration is implemented by Member 2. Without a real implementation and without a local profile, Operation intentionally has no fallback bean; this is fail-safe behavior.

## 7. Docker/profile contract

- `compose.member2.yml` is an isolated development artifact with exactly MySQL, Catalog, and Operation. It must not be blindly merged into root `docker-compose.yml`.
- The isolated Compose explicitly sets `SPRING_PROFILES_ACTIVE=docker-local` because Machine service is outside that topology.
- `LocalMachineSlotClient` and `LocalPostMaintenanceMachineClient` are active only in `local`, `test`, and `docker-local`.
- Full-team/integration/production orchestration **must not** enable `docker-local`; it must wire real Machine implementations instead.
- Application images are environment-neutral and do not enable the simulator by default.

## 8. Integration risks

**BLOCKER**

- None for Member 2 relay.

**NON-BLOCKING**

- Real Machine client beans are a required integration-layer/Member 4 deliverable before Operation can run in a non-local full-team profile. Enabling `docker-local` is not an acceptable integration fix.
- Host ports `8082` and `8085` must be coordinated with shared orchestration.
- `.env.example` is a shared root file; preserve the three Member 2 password variable names while resolving concurrent edits, and inject real secrets outside Git.
- Root Compose currently contains no Catalog/Operation wiring. The orchestration owner must translate the contracts above into shared Compose instead of copying the isolated topology wholesale.
- Shared build/runtime must support Java 21.
- The available Git metadata shows divergence from `origin/main` (3 main-only and 25 branch-only commits); integrate against the agreed baseline and resolve only actual integration conflicts.

## 9. Verification

- Compose config: PASS — `docker compose -p scentstation-member2 -f compose.member2.yml config --quiet`.
- Catalog package: PASS — `mvn -q -DskipTests package`.
- Operation package: PASS — `mvn -q -DskipTests package`.
- Catalog regression: PASS — 13 tests, 0 failures, 0 errors, 0 skipped.
- Operation regression: PASS — 265 tests, 0 failures, 0 errors, 0 skipped.
- Full regressions were run exactly once for this readiness task. DK-03 Docker lifecycle acceptance was trusted and not repeated.

## 10. Scope protection

- Root Compose modified by this task: NO.
- Identity, API Gateway, Order, Machine, Reporting, Frontend, or Device modified by this task: NO.
- Production code modified by this task: NO.
- Only this consolidated handoff was added.

## 11. Relay readiness

Member 2 ready for integration relay: **YES**.

Blockers: **0**. The remaining Machine wiring is an explicit cross-member integration dependency, not a hidden simulator fallback or an unresolved Member 2 defect.

## 12. Recommended relay action

Do **not** merge automatically.

1. Start from the agreed Member 1/integration baseline.
2. Bring in the Member 2 branch commits and the scoped working-tree groups listed in section 3; exclude unrelated dirty-worktree files.
3. Resolve only real integration conflicts, especially shared `.env.example`, ports, Java 21, and root Compose orchestration.
4. Add Catalog/Operation contracts to shared orchestration; do not merge `compose.member2.yml` wholesale.
5. Keep `docker-local` disabled. Supply real `MachineSlotClient` and `PostMaintenanceMachineClient` implementations when Machine service enters integration.
6. Run shared integration tests after wiring. Do not add Redis, message brokers, NiFi, or monitoring as part of this relay unless separately owned and approved.
