# Notification Service Phase 2 — Database & Core Architecture

## 1. Overview

- **Objective:** Implement the Notification Service's own persistence schema and core application layers, without adding the later management API, alert receiver, delivery provider, or system integration.
- **Starting state:** Phase 1 Spring Boot 3.4.5 / Java 21 foundation only; no notification schema, entity, repository, or business service.
- **Status:** **COMPLETED** for the Phase 2 scope based on successful H2 migration/schema validation and module tests. Live MySQL connectivity and cross-service behavior remain **NOT VERIFIED**.
- **Source of truth:** Current code was inspected before implementation. Historical Phase 0/1 reports are retained as historical records; the earlier `services/notification-service/docs/notification-service-phase-2.md` status snapshot was superseded and relocated after its contents were reviewed.

## 2. Repository and Design Findings

- `catalog-service` and `operation-service` already use Flyway (`flyway-core` and `flyway-mysql`); Notification Service follows this convention.
- JPA repository methods in existing services scope lookups by tenant. Notification Service follows that pattern for notification lookup and recipient inbox queries.
- Identity's `User.id` and `tenantId` are numeric, while Machine Service's alert event has a string `tenantId`. No shared ID mapping contract was verified. Notification Service stores tenant and recipient IDs as opaque strings and does not create cross-service foreign keys.
- The Machine Service `MachineAlertChangedEvent` provides `eventId`, `alertId`, `tenantId`, `machineId`, `locationId`, `alertType`, `severity`, `status`, `occurredAt`, and source. Phase 2 stores only source/source-event reference fields; event ingestion and field mapping remain Phase 4.
- The earlier status-only Phase 2 report had no approved schema. The implemented model is documented at `docs/database/notification-service-database-design.md`.

## 3. Database

Flyway migration: `services/notification-service/src/main/resources/db/migration/V1__create_notification_schema.sql`.

### `notifications`

- `id BIGINT` auto-increment primary key.
- `tenant_id VARCHAR(100)`, `title VARCHAR(200)`, `content VARCHAR(4000)`, `notification_type VARCHAR(50)`, `priority VARCHAR(20)`, and `source VARCHAR(50)` are required.
- `source_event_id VARCHAR(100)` is optional; `(source, source_event_id)` is unique for a future duplicate-event guard.
- `created_at` and `updated_at` use `DATETIME(6)`.
- Unique `(tenant_id, id)` supports a tenant-consistent local recipient foreign key.
- Index `(tenant_id, created_at, id)` supports tenant-scoped chronological reads.

### `notification_recipients`

- `id BIGINT` auto-increment primary key.
- `tenant_id VARCHAR(100)`, `notification_id BIGINT`, and `recipient_id VARCHAR(100)` are required.
- `read_at DATETIME(6)` is null while unread and set when read; `created_at` and `updated_at` are required.
- Local composite FK `(tenant_id, notification_id)` references `notifications(tenant_id, id)`.
- Unique `(notification_id, recipient_id)` and inbox index `(tenant_id, recipient_id, read_at, created_at, id)`.

There are no foreign keys to external services. Delivery channel/status tables and notification-wide lifecycle status were not added because Phase 5 owns delivery and recipient read state is modeled per recipient.

## 4. Core Architecture

### Entities

- `entity.Notification` — notification content/source/tenant and aggregate recipient association.
- `entity.NotificationRecipient` — opaque recipient identifier and independent read state.
- No enum was added: notification type and priority value sets/mapping are not yet agreed. They are bounded strings and must be validated against a future contract.

### Repositories

- `repository.NotificationRepository` — tenant-scoped `findByIdAndTenantId` and source-event existence check.
- `repository.NotificationRecipientRepository` — pageable tenant/recipient inbox, scoped recipient lookup, and unread count query.

### DTOs

- `dto.command.CreateNotificationCommand` — validated internal application command; requires tenant, content metadata, and one or more recipient IDs.
- `dto.response.NotificationResponse` — immutable response mapping, including recipient-specific `readAt`; entities are not exposed by the service layer.
- No HTTP request/response API was added.

### Service

- `service.NotificationService` creates a notification and recipient rows transactionally, deduplicates repeated recipient IDs, checks source-event duplication, maps to DTO, and offers a tenant-scoped lookup for later management APIs.
- It does not choose recipients, ingest Machine events, or deliver messages.

### Validation and exceptions

- Jakarta Bean Validation is applied to the internal create command and service method parameters.
- `exception.NotificationExceptionHandler` maps not-found, duplicate event, request validation, method constraint, and unreadable-body errors to a consistent response without rejected values or stack traces.
- Custom exceptions: `NotificationNotFoundException`, `DuplicateNotificationEventException`.
- No business controller exists in this phase; error mapping is tested through a test-only MVC controller.

## 5. Migration and Configuration

- Flyway dependencies: `flyway-core` and runtime `flyway-mysql`, matching Catalog/Operation.
- Flyway runs from `classpath:db/migration`.
- Hibernate remains `ddl-auto: validate`; test profile also uses `validate`, not `create-drop`.
- Port remains configurable through `NOTIFICATION_SERVICE_PORT`, default `8087`.
- Database remains `scentstation_notification`; `NOTIFICATION_DB_URL` retains the local development URL default.
- Database username and password now require `NOTIFICATION_DB_USERNAME` and `NOTIFICATION_DB_PASSWORD`; no credentials are embedded in configuration.

The Phase 1 report is preserved as a historical record. The current runtime configuration is authoritative.

## 6. Documentation Organization

- Updated `docs/ai-context/notification-service-context.md` in place, preserving prior verified findings and adding Phase 2 implementation details.
- Created `docs/database/notification-service-database-design.md`.
- Created this report at `docs/implementation-log/notification-service-phase-2.md`.
- Relocated the reviewed service-local AI context and Phase 2 snapshot into the canonical root documentation by merging/updating their contents.
- Moved the five future implementation prompts into `docs/ai-context/` with descriptive Phase 3–7 names and updated references. No new top-level documentation folder was added.
- Removed the superseded copies from `services/notification-service/docs/` only after the canonical replacements were written and checked. Phase 0/1 implementation logs were not modified.

## 7. Files Created or Modified

### Service implementation

- `services/notification-service/pom.xml`
- `services/notification-service/src/main/resources/application.yml`
- `services/notification-service/src/main/resources/db/migration/V1__create_notification_schema.sql`
- `services/notification-service/src/test/resources/application-test.properties`
- New entity, repository, command/response DTO, service, and exception classes under `services/notification-service/src/main/java/com/scentstation/notification/`
- New service and exception-handler tests under `services/notification-service/src/test/java/com/scentstation/notification/`

### Root documentation

- Updated `docs/ai-context/notification-service-context.md`
- Created `docs/database/notification-service-database-design.md`
- Created `docs/implementation-log/notification-service-phase-2.md`
- Created `docs/ai-context/notification-service-phase-3-prompt.md`
- Created `docs/ai-context/notification-service-phase-4-prompt.md`
- Created `docs/ai-context/notification-service-phase-5-prompt.md`
- Created `docs/ai-context/notification-service-phase-6-prompt.md`
- Created `docs/ai-context/notification-service-phase-7-prompt.md`

No source or configuration in other services was modified. No file was deleted outside the superseded Notification Service documentation copies.

## 8. Testing

Executed from `services/notification-service/`:

- First `mvn test -q`: **FAILED** because the test-only MVC controller was not registered in the slice test. The test setup was corrected; no production route was added.
- Subsequent `mvn test -q`: **PASSED**, 7 tests, 0 failures/errors/skips.
- Flyway applied V1 to H2 in MySQL compatibility mode; Hibernate validated entity mappings successfully.
- Existing `/actuator/health` smoke test passed.

- `mvn package -q` — **PASSED** from `services/notification-service/`; 7 tests, 0 failures, 0 errors, 0 skipped.
- `git diff --check` — **PASSED** from the repository root.

Environment: Maven 3.10.0, JDK 25.0.1; project compiler target is Java 21. The Flyway/H2 compatibility warning notes that H2 2.3.232 is newer than Flyway's tested H2 version; migration and schema validation nevertheless passed.

## 9. Not Implemented / Not Verified

### NOT IMPLEMENTED

- Notification Management REST APIs, authentication, authorization, and trusted tenant principal propagation.
- Machine Service event receiver, sender URL configuration, event processing, retry workflow, and full idempotent ingestion.
- Recipient resolution from Identity roles/assignments.
- Email, FCM, other channel delivery, templates, delivery status, and delivery retry.
- Gateway and Docker Compose integration.

### NOT VERIFIED

- Live MySQL compatibility, database credentials, and deployment migration execution.
- Cross-service tenant/user ID mapping and runtime service-to-service requests.
- Production security/deployment topology.

## 10. Design Changes and Rationale

- Chose Flyway because Catalog and Operation already use Flyway; no competing migration tool was added.
- Separated `notifications` content from `notification_recipients` read state, so one message can have distinct read state per recipient.
- Used opaque string IDs and no external foreign keys because Identity and Machine currently use differing tenant ID representations and service-owned databases.
- Kept type/priority as bounded strings because an authoritative value set/mapping is not established.
- Added a unique `(source, source_event_id)` constraint as a storage-level duplicate guard based on the verified Machine event ID; full receiver behavior remains Phase 4.
- Did not add a Controller or public API because those belong to Phase 3.

## 11. Final Status and Phase 3 Notes

**Phase 2: COMPLETED** for the independent database/core-architecture scope, based on the passing module tests and H2 migration/schema validation. Live MySQL remains **NOT VERIFIED**.

Before Phase 3, confirm the authentication/tenant authority that Notification Service can safely use; the service currently has no business API security. Use repository-level tenant-scoped recipient queries. Confirm how Identity user IDs map to the opaque `recipient_id`; do not infer recipients from a tenant-wide broadcast. Keep the event-to-notification content mapping for Phase 4.
