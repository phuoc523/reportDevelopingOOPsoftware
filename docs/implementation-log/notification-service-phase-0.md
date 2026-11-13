# Notification Service Phase 0 — Implementation Log

## 1. Investigation objective

This phase investigated the current repository to establish what is already implemented, what is only planned, and what remains unknown before any notification-service work is proposed. The goal was to verify the actual architecture, service boundaries, alerting flow, database ownership, security model, and gateway/deployment conventions using source evidence rather than documentation assumptions.

## 2. Repository branch and commit

- Branch: `test-identity-update`
- HEAD: `9ebd4dfa80fd626126f96ab7fde2b65b0ef5ab03`

## 3. CodeGraph availability and actual usage

- CodeGraph is available in this Copilot environment and a repository index exists at the repo root under `.codegraph/`.
- The `codegraph-codegraph_explore` tool was used for the required repo-wide architecture pass.
- Query used: `api-gateway identity-service catalog-service order-service machine-service operation-service reporting-service notification microservice services Java Spring Boot application properties pom.xml config controller service repository entity gateway`
- Result: symbol hits were found for service-to-service dependencies and key service classes, including `MachineServiceGateway`, `Order`, and `IdentityServiceApplication`.
- Limitation: the repository graph is not complete enough to map every `pom.xml` file uniquely, so direct source verification was still required. The CodeGraph pass was therefore used as the initial locator, not as the sole proof source.

## 4. Repository structure

Key repository areas verified from the live workspace:

- `README.md`: repo-level architecture notes; it describes an SOA/microservice architecture with a gateway and Java 21 + Spring Boot backend.
- `services/`: backend microservices.
- `frontend/`: `admin-web` and `kiosk-web` React apps.
- `device/`: firmware and simulator workspace.
- `infra/`: Docker- and infrastructure-side folders for `docker`, `mysql`, `mosquitto`, `nifi`, and `redis`.
- `contracts/`: protocol and API contract artifacts.
- `docs/`: product, deployment, security, and handoff documentation.
- `docker-compose.yml`: repository root compose file exists but is currently a skeleton (`services: {}` in the live file).
- `docker-compose.mysql.yml`: MySQL-focused infrastructure config exists.

## 5. Backend microservice inventory

Verified backend services in `services/`:

| Service | Directory | Verified responsibility | Evidence |
|---|---|---|---|
| api-gateway | `services/api-gateway` | Spring Cloud Gateway routing boundary and trust filtering | `services/api-gateway/src/main/resources/application.yml`, `services/api-gateway/src/main/java/com/scentstation/gateway/GatewayTrustBoundaryFilter.java` |
| identity-service | `services/identity-service` | JWT auth, RBAC, user account and tenant validation | `services/identity-service/src/main/resources/application.yml`, `SecurityConfig.java`, `JwtAuthenticationFilter.java` |
| catalog-service | `services/catalog-service` | catalog/inventory-facing service, MySQL + Flyway | `services/catalog-service/pom.xml`, `services/catalog-service/src/main/resources/application.yml` |
| order-service | `services/order-service` | order lifecycle and dispensing coordination with machine-service | `services/order-service/pom.xml`, `services/order-service/src/main/java/com/scentstation/order/service/DispensingService.java` |
| machine-service | `services/machine-service` | machine registry, slots, alerts, commands, alert handoff | `services/machine-service/README.md`, `database/scentstation_machine.sql`, `MachineAlertService.java`, `MachineAlertController.java` |
| operation-service | `services/operation-service` | operation/inventory + maintenance ticketing work | `services/operation-service/src/main/resources/application.yml`, `V1__initial_operation_schema.sql` |
| reporting-service | `services/reporting-service` | reporting analytics and transaction queries | `services/reporting-service/src/main/resources/application.yml`, reporting service sources |

Not found in the service directory list:

- `services/notification-service/` — no such directory exists in the current workspace.
- `services/maintenance-service/` — referenced in docs and in `machine-service/README.md` as a possible integration target, but no real service directory exists.

## 6. Technology stack

### Actively implemented

- Java 21 (`java.version` is set to `21` in the backend service POMs; verified in `services/api-gateway/pom.xml`, `services/identity-service/pom.xml`, `services/machine-service/pom.xml`, `services/order-service/pom.xml`, etc.)
- Spring Boot 3.3.5 to 3.5.6 depending on module (`services/api-gateway/pom.xml`, `services/identity-service/pom.xml`, `services/machine-service/pom.xml`, `services/order-service/pom.xml`)
- Spring Web / MVC / REST controllers
- Spring Data JPA
- Spring Security + JWT authentication in `identity-service`
- MySQL
- Flyway in `catalog-service` and `operation-service`
- Spring Cloud Gateway in `api-gateway`
- OpenAPI / Swagger via `springdoc-openapi-starter-webmvc-ui` in machine/order services
- H2 in test scope for some services
- Docker skeletons and Dockerfile usage for some services
- `RestClient`-based synchronous HTTP integration in order-service and machine-service outbox retry jobs

### Dependency exists but integration is not fully verified

- Redis: infrastructure folder exists, but no direct business-service integration code was verified in the backend services inspected.
- MQTT: present in `device/` and infrastructure directories, but the live `machine-service/README.md` explicitly says MQTT and device telemetry are not in this service’s scope.
- NiFi: infrastructure-only folder exists; no pipeline implementation was verified in the backend code inspected.
- Kafka/RabbitMQ: no actual producer/consumer implementation was found for the current service set.

### Configuration-only or documentation-only

- `notification-service` is referenced as a future integration target in `services/machine-service/README.md` and environment properties, but the actual service is missing.
- `maintenance-service` is similarly referenced as a future integration endpoint in machine alerts, but no such service exists in the repo.

## 7. Source-code conventions

Verified conventions in the implemented code:

- Package naming follows `com.scentstation.<service>.<layer>` (for example `com.scentstation.order.client`, `com.scentstation.identity.config`, `com.scentstation.service`, `com.scentstation.repository`, `com.scentstation.entity`).
- Application bootstrap classes are standard Spring Boot mains (`IdentityServiceApplication`, `OrderServiceApplication`, etc.).
- Dependency injection is constructor-based, with final fields and explicit `@Service` / `@RestController` components.
- JPA entities use `@Entity`, `@Table`, `@Column`, `@Enumerated`, and index definitions (`MachineAlert.java`, `AlertHandoffOutbox.java`).
- Validation is enforced with `@Valid` in controllers and custom validation exceptions (`MachineAlertController.java`, `MachineAlertService.java`).
- Transaction boundaries are enforced with `@Transactional` in service methods.
- HTTP metadata is configured via `@RequestHeader`, `@RequestParam`, `@PathVariable`, and `@Value` properties.
- API response DTOs are separate from entities (`MachineAlertResponse.java`, `LowStockAlertRequest.java`).
- Lombok is present in some service POMs (`catalog-service`, `operation-service`), but it is not a universal pattern across all modules.

## 8. Database architecture

Database ownership and initialization pattern are service-specific and not all fully aligned:

- `identity-service` uses MySQL at `jdbc:mysql://127.0.0.1:3307/scentstation_identity?createDatabaseIfNotExist=true` and `spring.jpa.hibernate.ddl-auto=update` (`services/identity-service/src/main/resources/application.yml`).
- `catalog-service` uses MySQL database `scentstation_catalog` and enables Flyway (`services/catalog-service/src/main/resources/application.yml`).
- `order-service` uses MySQL database `order_db` (default) with `spring.sql.init.mode=always` and `schema-locations: classpath:database/schema.sql` (`services/order-service/src/main/resources/application.yml`).
- `operation-service` uses MySQL database `scentstation_operation` and Flyway migrations under `src/main/resources/db/migration/` (`services/operation-service/src/main/resources/application.yml`, `services/operation-service/src/main/resources/db/migration/V1__initial_operation_schema.sql`).
- `reporting-service` uses MySQL database `scentstation_reporting` and `spring.jpa.hibernate.ddl-auto=validate` (`services/reporting-service/src/main/resources/application.yml`).
- `machine-service` uses MySQL database `scentstation_machine` with the canonical SQL schema file `services/machine-service/database/scentstation_machine.sql` and `spring.jpa.hibernate.ddl-auto=update` in `application.properties`.

### Machine-service database objects verified

Critical alerting and outbox tables are defined in `services/machine-service/database/scentstation_machine.sql`:

- `machine_locations`
- `machines`
- `machine_slots`
- `machine_history`
- `machine_alerts`
- `machine_commands`
- `alert_handoff_outbox`

The alert schema explicitly includes indexes, status checks, severity checks, and dedupe constraints. For example:

- `machine_alerts` has `idx_alert_tenant_status_time`, `idx_alert_machine_status`, and `idx_alert_dedupe_key`
- `alert_handoff_outbox` has `idx_alert_handoff_next_attempt`
- alert severity is restricted to `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- alert status is restricted to `OPEN`, `ACKNOWLEDGED`, `ASSIGNED`, `ESCALATED`, `RESOLVED`

## 9. Inter-service communication

### Synchronous HTTP communication

- API Gateway route definitions exist in `services/api-gateway/src/main/resources/application.yml` and route requests from `:8080` to backend service ports such as 8081..8086.
- `GatewayTrustBoundaryFilter` strips untrusted identity headers and rejects paths containing `internal` segments (`services/api-gateway/src/main/java/com/scentstation/gateway/GatewayTrustBoundaryFilter.java`).
- `order-service` calls `machine-service` through `MachineServiceGateway` and `DispensingService.submit()` / `DispensingService.getDispensingResult()` (`services/order-service/src/main/java/com/scentstation/order/service/DispensingService.java`).
- `machine-service` validates data with external integration endpoints using values from `machine.integration.operation-service-url` and `machine.integration.notification-service-url` in `services/machine-service/src/main/resources/application.properties`.

### Event/outbox handoff pattern

- `MachineAlertService.publish()` builds a `MachineAlertChangedEvent` and enqueues records in `alert_handoff_outbox` when the alert severity is `CRITICAL` or the alert status is `ESCALATED` (`services/machine-service/src/main/java/com/scentstation/service/MachineAlertService.java`).
- The outbox target is configured for `notification` and `maintenance` integrations.
- `AlertHandoffOutboxRetryJob` is scheduled to retry failed delivery via `RestClient` (`services/machine-service/src/main/java/com/scentstation/service/AlertHandoffOutboxRetryJob.java`).
- This is an actual handoff mechanism, even though the destination service is not implemented in this repository.

### Asynchronous/event-driven integration found

- `ApplicationEventPublisher` is used in `MachineAlertService` for `MachineAlertChangedEvent` publication after the transaction commit.
- No Kafka, RabbitMQ, or MQTT consumer implementation was found for notification delivery in the investigated code.

## 10. Existing alert and notification functionality

The repository contains real alerting logic in `machine-service`, not a standalone `notification-service` module.

### Alert lifecycle and ownership

- `MachineAlertController` exposes `GET /api/alerts`, `GET /api/alerts/{alertId}`, `POST /api/alerts/{alertId}/acknowledge`, `POST /api/alerts/{alertId}/assign`, `POST /api/alerts/{alertId}/resolve`, `POST /api/alerts/{alertId}/reopen`, plus the internal low-stock endpoint `POST /api/alerts/integration/low-stock` (`services/machine-service/src/main/java/com/scentstation/controller/MachineAlertController.java`).
- `MachineAlertService` deduplicates event streams with `dedupe_key`, observes repeated alerts, escalates overdue alerts, and resolves reopened alerts (
  `services/machine-service/src/main/java/com/scentstation/service/MachineAlertService.java`).
- `MachineAlert` entity tracks `OPEN`, `ACKNOWLEDGED`, `ASSIGNED`, `ESCALATED`, and `RESOLVED` lifecycle states; `MachineAlertType` includes `MACHINE_OFFLINE`, `SLOT_EMPTY`, `SENSOR_FAILURE`, `ACTUATOR_FAILURE`, `DOOR_OPEN`, `LEAK_DETECTED`, `DISPENSE_FAILED`, and `LOW_STOCK` (`services/machine-service/src/main/java/com/scentstation/entity/MachineAlert.java`, `MachineAlertType.java`).

### Handoff/outbox implementation

- `machine_alerts` table stores the alert record in MySQL.
- `alert_handoff_outbox` stores outbound event attempts for the `notification` and `maintenance` targets.
- `AlertHandoffOutboxRetryJob` retries failed alert handoff by calling downstream endpoints using `X-Internal-Service-Key`.
- `MachineAlertChangedEvent` includes `eventId`, `alertId`, `tenantId`, `machineId`, `locationId`, `alertType`, `severity`, `status`, and `occurredAt` (`services/machine-service/src/main/java/com/scentstation/event/MachineAlertChangedEvent.java`).

### Important limitation

No actual `notification-service` or `maintenance-service` implementation was found in the repository. The target endpoints are configured as external URLs only, and the outbox/retry mechanism makes clear that those services are expected to exist elsewhere or in a later phase.

## 11. Identity and security

- `identity-service` uses Spring Security with JWT parsing in `JwtAuthenticationFilter` (`services/identity-service/src/main/java/com/scentstation/identity/config/JwtAuthenticationFilter.java`).
- The filter verifies the JWT signature and expiry via `JwtService`, checks the `tokenVersion`, and rejects JWTs when the related tenant is not `ACTIVE`.
- `SecurityConfig` denies requests by default and only allows explicitly permitted routes (`services/identity-service/src/main/java/com/scentstation/identity/config/SecurityConfig.java`).
- `AccountPrincipal` and `AccessPolicy` define tenant and role checks for platform vs brand-level operations.
- `security.internal.service-key` is a mandatory configuration for private introspection calls and is enforced via `InternalServiceKeyVerifier` in `machine-service`.
- `GatewayTrustBoundaryFilter` strips caller-supplied identity headers such as `x-tenant-id`, `x-user-id`, `x-role`, `x-internal-service-key`, and `x-request-id` before forwarding the request upstream.
- `TenantGatewayFilter` in machine-service enforces `X-Tenant-Id` consistency for API calls when `machine.gateway.enforce-tenant-header` is enabled (`services/machine-service/src/main/java/com/scentstation/config/TenantGatewayFilter.java`).

## 12. Gateway and deployment

- `api-gateway` binds to port 8080 (`services/api-gateway/src/main/resources/application.yml`).
- Gateway route mapping includes:
  - identity-service => `http://localhost:8081`
  - catalog-service => `http://localhost:8082`
  - order-service => `http://localhost:8083`
  - machine-service => `http://localhost:8084`
  - operation-service => `http://localhost:8085`
  - reporting-service => `http://localhost:8086`
- `Service` port mapping is internally consistent in the gateway file, but `reporting-service` sets `server.port: 8085` in its config, which conflicts with operation-service port 8085 in `services/operation-service/src/main/resources/application.yml`.
- Root `docker-compose.yml` is an empty skeleton (`services: {}`) and does not wire the live services together.
- Some service modules have Dockerfiles or compose files, but they are not consistently wired into the root composition.
- `docker-compose.mysql.yml` exists for MySQL infrastructure, but the repo does not have a single composed environment for all backend services in the current root config.

## 13. Problems and inconsistencies

1. Root `README.md` states: “Current scope: repository structure only. Business logic is not implemented yet.” That statement is incomplete because concrete Java services and database schemas are present and partially implemented.
2. `notification-service` does not exist, even though `machine-service` references `MACHINE_NOTIFICATION_SERVICE_URL` and alert handoff code expects a notification target.
3. `maintenance-service` is also referenced in config and docs but is not present in the repository.
4. `reporting-service` uses port 8085 while `operation-service` also uses port 8085; the gateway routes operation and reporting to different upstream ports, but the local service config creates deployment ambiguity.
5. Root `docker-compose.yml` is a placeholder, despite the repo having multiple backend services and explicit port assignments.
6. `machine-service` uses `ddl-auto=update` while other services favor `validate` or schema automation; the database strategy is not fully standardized.
7. Some infrastructure tags (MQTT, NiFi, Redis) are present in the repo structure, but not all have matching runtime implementations or service wiring in the backend code inspected.

## 14. Potential integration points

The following are the most obvious integration points for a future `notification-service`:

- `MachineAlertChangedEvent` publication from `MachineAlertService.publish()`.
- `alert_handoff_outbox` table as the durable outbound queue.
- `AlertHandoffOutboxRetryJob` as the retry mechanism.
- `MACHINE_NOTIFICATION_SERVICE_URL` env var in `machine-service/README.md` and `services/machine-service/src/main/resources/application.properties`.
- `api-gateway` route boundary and trust filtering for public HTTP ingress.
- `identity-service` for tenant-aware user and role validation.
- `operation-service` as a possible downstream maintenance/orchestration consumer of escalated critical alerts.

## 15. Unknowns and investigation limitations

- The exact downstream notification payload contract is not fully defined beyond the event object fields and outbox row contents.
- No repository-local consumer implementation for a notification receiver was found.
- No email/push/SMS delivery implementation or platform integration was found in the current codebase.
- The final runtime deployment topology is not yet determined because root Docker Compose is still a skeleton.
- Whether `notification-service` should be a dedicated microservice, a thin event listener, or an integration endpoint is still unknown from current code and docs.
- There is no verified GitHub issue or external service contract describing the eventual `notification-service` behavior in this repository snapshot.

## 16. Final assessment

The repository contains a real alerting and handoff mechanism in `machine-service`, but it is not yet backed by a working `notification-service` implementation. The closest live implementation is the `MachineAlert` domain model, the `machine_alerts` table, the `alert_handoff_outbox` table, `MachineAlertChangedEvent`, and the scheduled retry job that posts to a configured downstream endpoint.

At the current state of the repo, a new notification microservice would need to integrate with an existing alert event source and likely satisfy the `notification` target expected by `machine-service`, but the repository does not currently contain that service or an end-to-end delivery implementation.

Status: Notification Service has NOT been implemented.
