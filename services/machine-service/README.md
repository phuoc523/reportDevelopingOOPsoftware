# ScentStation Machine Service

Implements the first three tasks assigned to Member 4:

- **P4-01 — Machine Registration, Tenant & Location Management**
- **P4-02 — Slot Configuration & Availability Management**
- **P4-03 — Operational Status & Remote Control**

Technology: Java 21, Spring Boot 3.4.5, Spring Web, Spring Data JPA, Bean Validation, MySQL.

## Run locally

1. Install JDK 21 and Maven.
2. Create the database by running `database/machine_service.sql` in MySQL Workbench, or create a database named `scentstation_machine`.
3. Configure environment variables if needed:
   - `MACHINE_DB_URL` (default `jdbc:mysql://localhost:3306/scentstation_machine?...`)
   - `MACHINE_DB_USERNAME` (default `root`)
   - `MACHINE_DB_PASSWORD` (default blank)
   - `MACHINE_SERVICE_PORT` (default `8084`)
4. From this directory, run `mvn spring-boot:run`.
5. Run tests with `mvn clean test`.

`spring.jpa.hibernate.ddl-auto=update` is enabled for local development only. The SQL file is provided separately as the database schema reference. For team deployment, use a migration tool and coordinate schema changes.

## Important integration boundaries

- `tenantId`, `productId`, and `cartridgeId` are identifiers owned by other services. Machine locations are managed locally by `/api/locations`; no cross-service foreign keys are created.
- The service does **not** implement authentication/RBAC yet. Protect management endpoints with the shared Identity/API Gateway security contract before integration.
- `X-Actor-Id` is a temporary development header for history records, not authentication.
- The API currently accepts `tenantId` in requests for local development. In production, derive/validate tenant scope from the authenticated identity; do not trust a client-provided tenant ID.
- Slot availability requires an active device, machine status `ONLINE`, enabled slot, `AVAILABLE` slot status, both `productId` and `cartridgeId`, and the current time to be within configured operating hours. If no hours are configured, there is no time restriction.
- A simple HTTP heartbeat endpoint updates `lastSeenAt`, but MQTT ingestion, device authentication, automatic timeout detection, and actual hardware commands are not implemented yet.


### Location setup
Create a location before registering a machine. Use `POST /api/locations` with `locationId`, `tenantId`, `name`, and optional `address`. Listing locations requires `GET /api/locations?tenantId=...`. Machine registration validates that the location exists, is active, and belongs to the selected Tenant.

### Slot price and audit event
Slot create/update requests include `price` (decimal, non-negative). Machine history is persisted and a `MachineAuditEvent` Spring application event is published for each history action. A local Spring application event is published; a message-broker/shared-Audit adapter still needs to be connected by the integration layer.

### Heartbeat
`POST /api/machines/{machineId}/heartbeat` updates `lastSeenAt`; an OFFLINE/UNSTABLE active device becomes ONLINE. This demo endpoint does not authenticate device credentials; add device authentication before production.

### Tests
Unit tests cover operating-hours rules (including overnight schedules), tenant-scoped listing, and location validation during registration. Run `mvn clean test` from this service folder.
