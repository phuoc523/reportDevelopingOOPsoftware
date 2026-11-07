# DB-02 — Catalog Persistence Foundation Handoff

## 1. Objective

Establish the Catalog service persistence foundation on MySQL with versioned Flyway schema management, Hibernate schema validation, and tenant-safe product reads and mutations. This handoff covers DB-02 only; DB-03 has not been started.

## 2. Git and branch state

- Branch: `feature/maintenance`
- Starting/current HEAD: `3ddd61880d6262703350f9de30fead979dfd1b0c`
- No commit, push, merge, rebase, or checkout was performed.
- Pre-existing worktree changes outside the DB-02 scope were preserved.

## 3. Files inspected

- Catalog Maven build and runtime/test configuration
- Catalog application entry point, controller, DTOs, entity, repository, service, mapper, and exception handling
- Existing Catalog MVC tests and Dockerfile
- Catalog call paths and persistence symbols through CodeGraph
- Existing repository status and DB-01 audit context

## 4. Files changed

Production code:

- `services/catalog-service/src/main/java/com/scentstation/catalog/controller/ProductController.java`
- `services/catalog-service/src/main/java/com/scentstation/catalog/entity/Product.java`
- `services/catalog-service/src/main/java/com/scentstation/catalog/repository/ProductRepository.java`
- `services/catalog-service/src/main/java/com/scentstation/catalog/service/ProductService.java`

Dependencies and configuration:

- `services/catalog-service/pom.xml`
- `services/catalog-service/src/main/resources/application.yml`
- `services/catalog-service/src/test/resources/application.yml`
- `services/catalog-service/src/test/resources/docker-java.properties`

Migration:

- `services/catalog-service/src/main/resources/db/migration/V1__initial_catalog_schema.sql`

Tests:

- `services/catalog-service/src/test/java/com/scentstation/catalog/controller/ProductControllerTest.java`
- `services/catalog-service/src/test/java/com/scentstation/catalog/ProductTenantIsolationIntegrationTest.java`
- `services/catalog-service/src/test/java/com/scentstation/catalog/CatalogMySqlIntegrationTest.java`

Documentation:

- `docs/db-02-catalog-persistence-foundation-handoff.md`

No file under `services/operation-service/**` was changed.

## 5. Schema ownership

Catalog owns the physical `products` table. The migration intentionally contains no foreign key to a tenant table because tenant lifecycle and tenant schema are outside Catalog's database ownership boundary.

## 6. Physical schema

`products` contains:

- `id BIGINT AUTO_INCREMENT` primary key
- `tenant_id BIGINT NOT NULL`
- `name VARCHAR(255) NOT NULL`
- `description VARCHAR(1000) NULL`
- `fragrance_notes VARCHAR(500) NULL`
- `image_url VARCHAR(255) NULL`
- `default_price DECIMAL(12,2) NOT NULL`
- `available BOOLEAN NOT NULL`
- `created_at DATETIME(6) NOT NULL`
- `updated_at DATETIME(6) NOT NULL`

The JPA entity explicitly maps these physical column names, lengths, precision, scale, and index metadata.

## 7. Tenant isolation fix

Before DB-02, list and ID-based reads used global `findAll()` and `findById()`. Price and availability mutations also resolved a product globally by ID. A caller could therefore read or mutate another tenant's product if its ID was known.

After DB-02:

- Lists use `findAllByTenantId(tenantId)`.
- ID reads and mutations use `findByIdAndTenantId(id, tenantId)`.
- Cross-tenant lookup follows the same not-found path as a missing product.
- Tests prove another tenant cannot read, change price, or change availability.

No production Catalog path uses a global product lookup for these operations.

## 8. Flyway configuration

Maven dependencies added under Spring Boot dependency management:

- `org.flywaydb:flyway-core:10.20.1`
- `org.flywaydb:flyway-mysql:10.20.1` with runtime scope

Flyway remains enabled by Spring Boot in normal runtime configuration and discovers migrations from the standard `classpath:db/migration` location.

## 9. V1 migration

`V1__initial_catalog_schema.sql` creates the initial `products` table, primary key, tenant access index, and non-negative price check. It is an additive baseline for an empty Catalog database; no destructive data migration was introduced.

## 10. Runtime Hibernate policy

Normal runtime configuration now uses:

- `spring.jpa.hibernate.ddl-auto=validate`
- `spring.jpa.open-in-view=false`

Flyway owns schema creation and evolution. Hibernate checks entity/schema agreement at startup and does not create or update production tables.

## 11. H2 test strategy

Fast H2 tests deliberately keep:

- Flyway disabled
- Hibernate `create-drop`
- MySQL compatibility mode
- Open Session in View disabled

This keeps controller and service regression feedback fast. The explicit H2 dialect was removed so that the MySQL Testcontainers context can select the correct dialect from its JDBC metadata.

## 12. MySQL Testcontainers strategy

- Testcontainers dependencies: `junit-jupiter` and `mysql`, version `1.20.6`
- Pinned database image: `mysql:8.0.41`
- The test supplies container JDBC credentials dynamically.
- Flyway is enabled and Hibernate uses `ddl-auto=validate` in the MySQL context.
- `docker-java.properties` pins Docker API `1.44` for compatibility between the Spring Boot-managed Testcontainers version and the local Docker Engine 29 installation.
- If Docker is unavailable, the MySQL class is explicitly marked to skip; the recorded acceptance runs below were executed with Docker available and skipped zero tests.

## 13. Index design

The composite index `idx_products_tenant_id_id (tenant_id, id)` supports both required access shapes:

- Tenant catalog list through the leftmost `tenant_id` prefix
- Exact tenant-scoped product lookup through both columns

The primary key already supports internal ID uniqueness, so a second standalone ID index is unnecessary.

## 14. Constraints and validation

- Database check: `chk_products_default_price_non_negative CHECK (default_price >= 0)`
- Required product fields remain `NOT NULL` in the schema.
- Existing request validation remains the first application-level guard.
- The MySQL test proves a direct negative-price persistence attempt is rejected by the database.
- No unique constraint was added to product name because no tenant-scoped product business key is currently defined.

## 15. Timestamp behavior

`created_at` and `updated_at` are required `DATETIME(6)` columns. Existing JPA lifecycle callbacks populate both on insert and refresh `updated_at` on update. DB-02 preserves this application-managed timestamp policy.

## 16. API contract changes

Tenant context is now required as a query parameter for operations that previously had no tenant discriminator:

- `GET /api/products?tenantId={tenantId}`
- `GET /api/products/{id}?tenantId={tenantId}`
- `PATCH /api/products/{id}/price?tenantId={tenantId}`
- `PATCH /api/products/{id}/availability?tenantId={tenantId}`

`POST /api/products` is unchanged because its request body already carries `tenantId`.

## 17. Tests added or updated

- Updated 10 MVC tests for the tenant-aware controller/service contract.
- Added one H2 integration test covering same-tenant list/read and cross-tenant read/mutation rejection.
- Added two real-MySQL integration tests covering Flyway bootstrap, Hibernate validation, insert, tenant list/read, cross-tenant rejection, and database rejection of a negative price.

## 18. Verification results

All commands were run from `services/catalog-service`.

- `mvn test`: **BUILD SUCCESS** — 13 run, 0 failures, 0 errors, 0 skipped.
- `mvn clean test`: **BUILD SUCCESS** — 13 run, 0 failures, 0 errors, 0 skipped.
- `mvn -Dtest=CatalogMySqlIntegrationTest test`: **BUILD SUCCESS** — 2 run, 0 failures, 0 errors, 0 skipped.
- MySQL logs show Flyway applying V1 before the JPA context starts successfully with schema validation.
- `git diff --check`: passed.
- Diff check for `services/operation-service`: empty.

## 19. Known gaps

- There is still no authenticated principal or trusted gateway tenant context; callers currently supply `tenantId` explicitly.
- Product name has no declared business-key semantics, so duplicate names within a tenant remain allowed.
- Pagination, sorting, soft deletion, and optimistic locking are not part of DB-02.
- The V1 migration targets empty/new Catalog databases. Adoption for an already-populated unmanaged production schema requires a separately planned Flyway baseline procedure.
- Docker Compose/service deployment wiring was not changed in DB-02.

## 20. P0 resolution status

- P0 — global tenant-unscoped Catalog reads: **RESOLVED**.
- P0 — global tenant-unscoped price/availability mutations: **RESOLVED**.

## 21. Flyway readiness

**READY.** Dependencies, runtime discovery, V1 schema, real-MySQL bootstrap, and Hibernate validation have all been exercised successfully.

## 22. Docker readiness

**READY WITH LOCAL COMPATIBILITY CONFIGURATION.** The real-MySQL suite passes on Docker Desktop/Engine 29 using Docker API 1.44. No deployment Compose changes were required or made.

## 23. DB-03 readiness

**YES.** DB-02 provides a versioned Catalog schema and tenant-safe persistence boundary suitable for follow-up database work. This task did not implement any DB-03 scope.

## 24. Recommendation

Mark **DB-02 DONE**. Begin DB-03 only as a separate scoped task, preserving Flyway as the schema authority and the tenant-qualified repository pattern established here.
