# Notification Service AI Context

## 1. Project overview

This repository is a Java 21 / Spring Boot microservice monorepo for ScentStation, an IoT smart perfume kiosk system. The live repository contains service directories under `services/`, React frontends under `frontend/`, hardware and simulator code under `device/`, and infrastructure skeletons under `infra/`, `docker-compose.yml`, and `docker-compose.mysql.yml`.

The repository is not a single app; it is a multi-service codebase with separate business boundaries and partial implementations.

## 2. Current repository branch and commit

- Branch: `test-identity-update`
- HEAD: `9ebd4dfa80fd626126f96ab7fde2b65b0ef5ab03`

## 3. Backend service inventory

Verified backend services:

- `api-gateway` — Spring Cloud Gateway at port 8080. Routes public requests to backend services and strips untrusted identity headers.
- `identity-service` — JWT-based authentication and RBAC; user + tenant logic.
- `catalog-service` — catalog/inventory service with MySQL + Flyway.
- `order-service` — order lifecycle and machine dispensing coordination.
- `machine-service` — machine/alert/dispense command service; actual alerting logic lives here.
- `operation-service` — maintenance/inventory operation service with Flyway schema.
- `reporting-service` — reporting and transaction query service.

- `notification-service` — independent service at port 8087; Phase 2 persistence/core layers now exist, while business HTTP APIs, event receiver, recipient resolution, and delivery remain unimplemented.

## 4. Technology versions

- Java: 21
- Spring Boot: 3.3.5 / 3.4.5 / 3.5.6 depending on service
- Spring Cloud Gateway: present in `api-gateway`
- Spring Security: present in `identity-service`
- Spring Data JPA: present in all Java services
- MySQL: active in all service configs
- Flyway: active in `catalog-service` and `operation-service`
- Notification Service now also uses Flyway Core + Flyway MySQL, matching Catalog/Operation migration conventions
- OpenAPI / Swagger: present in machine/order service dependencies
- H2: used for tests
- Docker: structure exists, but root compose is a skeleton

## 5. Service responsibilities

- `api-gateway`: public routing boundary and trust filtering; blocks `/internal` paths and strips sensitive headers.
- `identity-service`: authentication, user account management, tenant checks, permission policies.
- `order-service`: order confirmation, payment flow, dispensing orchestration to machine-service.
- `machine-service`: machine lifecycle, device/slot state, command execution, and alerting; this is the only place where actual alert logic is implemented.
- `operation-service`: maintenance and operational inventory logic.
- `reporting-service`: transaction and operational reporting.

## 6. Source-code conventions

Observed patterns in live code:

- `com.scentstation.<service>.<layer>` package layout.
- Constructor injection and final fields.
- `@Service`, `@RestController`, `@Configuration`, `@Entity`, `@Repository` usage.
- `@Transactional` at service boundaries.
- DTOs separated from entity classes.
- `@Value` for environment configuration.
- `@Valid` + custom exceptions for request validation.
- `MachineAlert` and related classes use JPA indexes and status enums.

## 7. Database architecture

- There is no single shared schema for the repo; the backend services configure separate MySQL databases.
- Service DBs verified:
  - `identity-service` -> `scentstation_identity`
  - `catalog-service` -> `scentstation_catalog`
  - `order-service` -> `order_db` (default)
  - `machine-service` -> `scentstation_machine`
  - `operation-service` -> `scentstation_operation`
  - `reporting-service` -> `scentstation_reporting`
  - `notification-service` -> `scentstation_notification`
- `machine-service` owns the canonical machine schema in `services/machine-service/database/scentstation_machine.sql`.
- `operation-service` uses Flyway migration `V1__initial_operation_schema.sql`.
- `machine-service` alert tables include `machine_alerts` and `alert_handoff_outbox`.
- Notification Service schema is managed by `services/notification-service/src/main/resources/db/migration/V1__create_notification_schema.sql`; Hibernate uses `ddl-auto: validate`.
- Notification tables are `notifications` and `notification_recipients`; the latter holds per-recipient `read_at` state. External tenant/user IDs are opaque and have no cross-service foreign keys.
- H2 in MySQL mode applied V1 and Hibernate validated the mappings in tests. Live MySQL connectivity remains NOT VERIFIED.

## 8. Inter-service communication

Actual communication patterns found:

- API Gateway routes external traffic to backend services.
- `order-service` -> `machine-service` via a `MachineServiceGateway` and `RestClient`-style HTTP calls.
- `machine-service` uses an outbox/retry pattern for critical alert handoff.
- `machine-service` is configured with `machine.integration.operation-service-url`, `machine.integration.notification-service-url`, and `machine.integration.maintenance-service-url` in `application.properties`.
- No Kafka/RabbitMQ/MQTT event bus for notification delivery was found.

## 9. Existing alert/notification implementation

The repository does have a real alerting implementation, but it is in `machine-service`, not a separate service.

- `MachineAlertController` exposes alert CRUD and lifecycle actions.
- `MachineAlertService` creates, updates, deduplicates, acknowledges, assigns, resolves, reopens, and escalates alerts.
- `MachineAlert` entity includes lifecycle states and severity levels.
- `MachineAlertChangedEvent` is emitted when alert state changes.
- `AlertHandoffOutbox` and `AlertHandoffOutboxRetryJob` create a durable retryable handoff to downstream services.
- `machine_alerts` and `alert_handoff_outbox` tables are in the machine database.
- The current repo does not contain a working `notification-service` listener or receiver.
- Notification Service stores optional `(source, source_event_id)` with a unique constraint, but Phase 2 did not add event ingestion or processing.

## 10. Security and tenant isolation

- `identity-service` uses JWT + Spring Security.
- `JwtAuthenticationFilter` validates token signatures, token version, and active tenant status.
- `SecurityConfig` enables stateless auth and denies everything by default.
- `GatewayTrustBoundaryFilter` strips caller-supplied identity headers and rejects `internal` path segments.
- `machine-service` enforces `X-Tenant-Id` consistency when configured through `TenantGatewayFilter`.
- `InternalServiceKeyVerifier` enforces a shared secret for internal calls and low-stock alert ingestion.

## 11. Gateway and deployment

- API Gateway port: 8080
- Route targets: 8081..8086 according to `services/api-gateway/src/main/resources/application.yml`
- Root `docker-compose.yml` is currently a skeleton; no actual multi-service composition is defined there.
- Some services have Dockerfiles or compose files, but they are not wired together at the root level.
- `reporting-service` uses port 8085 while `operation-service` also uses port 8085; this is a deployment inconsistency that should be reviewed.

## 12. Potential integration points

Likely future touchpoints for notification-service work:

- `MachineAlertChangedEvent` publication from `MachineAlertService.publish()`
- `alert_handoff_outbox` as the durable queue for alert delivery
- `AlertHandoffOutboxRetryJob` as the retry scheduler
- `MACHINE_NOTIFICATION_SERVICE_URL` environment setting
- `identity-service` for tenant-aware user and role validation
- `api-gateway` for public ingress and trust boundary enforcement
- `operation-service` as a potential maintenance/alert consumer for escalated events

## 13. Known problems

- `notification-service` now has a Phase 1 foundation, but it does not yet include a receiver or listener for the alert handoff that `machine-service` expects.
- `maintenance-service` is referenced but not implemented.
- Root compose is a skeleton, not a running deployment definition.
- `reporting-service` and `operation-service` both use port 8085, causing deployment ambiguity.
- Documentation states the repo is “structure-only,” but the codebase includes substantial service and alerting logic.

## 14. Unknowns requiring investigation

- Exact downstream payload contract for notification delivery.
- Whether notification integration should be a dedicated service, a shared consumer, or an endpoint-based integration.
- Whether email, SMS, or push delivery is intended.
- Whether there is a valid deployment environment outside this repo snapshot.
- Whether `maintenance-service` and `notification-service` are intentionally absent from this repository or planned for another branch/repo.

## 15. Constraints for notification-service implementation

- Do not assume a new technology stack without verifying repo conventions.
- Follow the existing package conventions: `com.scentstation.<service>...` and Spring Boot 3.x.
- Respect existing tenant-aware and internal-service-key security expectations.
- Treat `machine-service` as the current alert source of truth.
- Preserve the existing outbox/retry pattern rather than creating a separate, incompatible mechanism.
- Keep the implementation aligned with the current MySQL-based service model and API Gateway routing design.

## 16. Current status

Phase 1 foundation is implemented: a standalone `services/notification-service` module exists with Java 21, Spring Boot 3.4.5, environment-based MySQL configuration for `scentstation_notification`, actuator health support, and a Dockerfile.

Phase 2 is implemented and H2-verified: Flyway migration, notification/recipient entities, repositories, internal service/DTOs, validation, and error handling exist. `mvn test -q` and `mvn package -q` pass. The service is not fully integrated: business APIs, authentication/authorization, event ingestion, recipient resolution, and delivery remain unimplemented.

## 17. Phase 1 completion notes

- Service directory: `services/notification-service`
- Main application class: `com.scentstation.notification.NotificationServiceApplication`
- Maven / Spring Boot: Java 21 + Spring Boot 3.4.5
- Selected application port: `8087`
- Database name: `scentstation_notification`
- Database env vars: `NOTIFICATION_DB_URL`, `NOTIFICATION_DB_USERNAME`, `NOTIFICATION_DB_PASSWORD`, `NOTIFICATION_SERVICE_PORT`
- Database username and password are required environment values; neither is hardcoded.
- Health endpoint: `/actuator/health`
- Dockerfile status: implemented, following repository conventions
- Build and test status: Phase 2 `mvn test -q` and `mvn package -q` passed using Maven 3.10.0/JDK 25; the project target remains Java 21. Live MySQL was not tested.
- Implemented packages: `entity`, `repository`, `dto.command`, `dto.response`, `service`, and `exception`.
- Implemented core classes: `Notification`, `NotificationRecipient`, `NotificationRepository`, `NotificationRecipientRepository`, `CreateNotificationCommand`, `NotificationResponse`, `NotificationService`, and `NotificationExceptionHandler`.
- Database design: `docs/database/notification-service-database-design.md`.
- Phase 2 implementation log: `docs/implementation-log/notification-service-phase-2.md`.
- Next phase: Phase 3 — Notification Management API. Confirm trusted authentication/tenant authority and recipient ID mapping before exposing data.

## 18. Phase 2 data model and implementation boundaries

- `notifications` stores tenant ID, title, content, type, priority, source, optional source event ID, and timestamps.
- `notification_recipients` stores local notification/tenant association, opaque recipient ID, per-recipient read timestamp, and timestamps.
- A composite local FK enforces that the recipient row tenant matches the parent notification tenant. No FK reaches Identity or Machine databases.
- `(source, source_event_id)` prevents duplicate non-null source event identifiers at the database layer. The Machine Service event receiver and complete idempotent handling remain Phase 4.
- Type and priority are strings because no authoritative value mapping has been approved. No delivery status, template, or notification-wide read flag is implemented.
- `NotificationService` is an internal application service; it is not exposed as a controller/API. The only verified HTTP endpoint remains Actuator health.
- Notification Service currently has no business authentication, authorization, trusted principal, or tenant isolation filter. Tenant-scoped repository queries are foundational safeguards, not proof of authorized caller identity.

## 19. Future phase prompts

The implementation prompts are maintained in the existing root AI-context directory:

- `docs/ai-context/notification-service-phase-3-prompt.md`
- `docs/ai-context/notification-service-phase-4-prompt.md`
- `docs/ai-context/notification-service-phase-5-prompt.md`
- `docs/ai-context/notification-service-phase-6-prompt.md`
- `docs/ai-context/notification-service-phase-7-prompt.md`

The service-local `services/notification-service/docs/` copies were relocated after comparison; use these root documentation paths.
