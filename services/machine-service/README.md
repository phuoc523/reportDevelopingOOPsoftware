# ScentStation Machine Service

REST service for machine, location, slot, availability, alerts, and dispensing commands (P4-01 to P4-09). MQTT and device telemetry are not part of this service scope.

## Database

Run `database/scentstation_machine.sql` once. It creates the MySQL schema and does not drop an existing database.

```powershell
mysql -u root -p < database\scentstation_machine.sql
```

Tenant, location, product, cartridge, and order IDs are external identifiers. The service uses no cross-service foreign keys for them.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| MACHINE_DB_URL | local MySQL URL | JDBC URL |
| MACHINE_DB_USERNAME | root | Database user |
| MACHINE_DB_PASSWORD | blank | Database password |
| MACHINE_SERVICE_PORT | 8084 | HTTP port |
| MACHINE_OPERATING_HOURS_ZONE | Asia/Ho_Chi_Minh | Operating-hours timezone |
| MACHINE_COMMAND_EXPIRY_INTERVAL_MS | 60000 | Command expiry job interval |
| MACHINE_COMMAND_LIFETIME_MS | 60000 | Dispensing-command lifetime |
| MACHINE_ALERT_ESCALATE_AFTER_MS | 300000 | Alert escalation delay |

```powershell
# For this PowerShell session (never commit a real password):
$env:MACHINE_DB_PASSWORD="your-local-password"
mvn spring-boot:run
```

## Dispensing API

- POST `/api/machines/dispense-commands` accepts an order dispensing command and returns HTTP 202.
- GET `/api/machines/dispense-commands/{commandId}` returns the current command status.

A command ID is idempotent. An order cannot have more than one active command. Machine and slot availability are validated before acceptance.

Statuses are `ACCEPTED`, `PROCESSING`, `ACKNOWLEDGED`, `DISPENSED`, `FAILED`, `EXPIRED`, and `UNKNOWN`.

## Maintenance API

- `PATCH /api/machines/{machineId}/control` with `ENTER_MAINTENANCE` puts the machine into maintenance mode and blocks new dispensing.
- `POST /api/machines/{machineId}/maintenance-completion` records `HARDWARE_INSPECTION`, `CLEANING`, and `CALIBRATION_CHECK`, plus the post-maintenance test result.
- `PATCH /api/machines/{machineId}/control` with `ENABLE` returns a maintenance-completed machine to `OFFLINE`; the machine cannot leave maintenance unless the checklist and post-maintenance test both pass.

Example completion body:

```json
{
  "checklistItems": ["HARDWARE_INSPECTION", "CLEANING", "CALIBRATION_CHECK"],
  "postMaintenanceTestPassed": true,
  "actorId": "technician-01"
}
```

## Service integrations

Configure external services through environment variables:

```text
MACHINE_INTEGRATION_SHARED_SECRET=<shared Order/API Gateway secret>
MACHINE_OPERATION_SERVICE_URL=http://operation-service:8085
MACHINE_NOTIFICATION_SERVICE_URL=http://notification-service/api/integration/alerts
MACHINE_MAINTENANCE_SERVICE_URL=http://maintenance-service/api/integration/alerts
MACHINE_GATEWAY_ENFORCE_TENANT_HEADER=true
```

When operation-service URL is configured, slot creation/update validates both product and cartridge references against it. Dispense and internal low-stock endpoints require `X-Internal-Service-Key`. Tenant-aware Gateway mutations require `X-Tenant-Id`. Escalated and CRITICAL alerts are handed off after transaction commit.

Machine/Slot/Alert OpenAPI is available at `/v3/api-docs` and Swagger UI at `/swagger-ui/index.html`.

## Tests

```powershell
mvn clean test
```

Tests use the standard Maven layout and an isolated H2 database.
