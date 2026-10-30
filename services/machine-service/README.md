# ScentStation Machine Service
- **P4-01 — Machine Registration, Tenant & Location Management**
- **P4-02 — Slot Configuration & Availability Management**
- **P4-03 — Operational Status & Remote Control**
- **P4-04 — MQTT Device Communication & Authentication**
- **P4-05 — Heartbeat, Telemetry & Device Events**
- **P4-06 — Machine Alerts & Lifecycle**

## Create the database

The schema script is [database/scentstation_machine.sql](./database/scentstation_machine.sql). It creates the `scentstation_machine` database and its tables for machines, slots, history, locations, telemetry, device events, message receipts, commands, and alerts.

Run the script in MySQL Workbench, or from Command Prompt (`cmd`) with the MySQL client:

```cmd
mysql -u root -p < database\scentstation_machine.sql
```

The script does not drop or recreate an existing database. Its `CREATE TABLE IF NOT EXISTS` statements initialize missing tables but do not migrate existing ones.

For a database created before P4-04–P4-06, apply the following once before deploying the new application; the schema script cannot add columns to an existing `machines` table:

```sql
ALTER TABLE machines
  ADD COLUMN last_heartbeat_event_id VARCHAR(100) NULL,
  ADD COLUMN firmware_version VARCHAR(100) NULL,
  ADD COLUMN configuration_version VARCHAR(100) NULL;
```

After adding the machine columns, rerun the current schema script to create any missing telemetry, device-event, alert, command, and message-receipt tables. `CREATE TABLE IF NOT EXISTS` will preserve existing tables. If any machine columns already exist, omit those columns from the `ALTER TABLE` statement.
If `machine_alerts` was created by an earlier P4-04–P4-06 build, also add its source column:

```sql
ALTER TABLE machine_alerts
  ADD COLUMN alert_source VARCHAR(20) NOT NULL DEFAULT 'DEVICE';
```

The schema intentionally uses external IDs for tenants, products, and cartridges instead of foreign keys to other services. Machine slots and machine history have foreign keys to `machines`.

Each non-null cartridge ID can be assigned to only one slot across the whole service. For an existing database, first check for reused cartridges with `SELECT cartridge_id, COUNT(*) FROM machine_slots WHERE cartridge_id IS NOT NULL GROUP BY cartridge_id HAVING COUNT(*) > 1;`. Resolve any duplicates before applying `ALTER TABLE machine_slots ADD CONSTRAINT uk_machine_slot_cartridge UNIQUE (cartridge_id);`. MySQL's default case-insensitive collation also prevents case-only duplicate IDs.

## Configure and run

The default connection is to `127.0.0.1:3306`, database `scentstation_machine`, with username `root` and a blank password. Override these defaults with environment variables when needed:

| Variable | Default | Purpose |
|---|---|---|
| `MACHINE_DB_URL` | `jdbc:mysql://127.0.0.1:3306/scentstation_machine?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` | JDBC connection URL |
| `MACHINE_DB_USERNAME` | `root` | MySQL username |
| `MACHINE_DB_PASSWORD` | blank | MySQL password |
| `MACHINE_SERVICE_PORT` | `8084` | HTTP port |
| `MACHINE_OPERATING_HOURS_ZONE` | `Asia/Ho_Chi_Minh` | Time zone used to evaluate operating hours |
| `MACHINE_MQTT_ENABLED` | `false` | Enable TLS MQTT device ingestion and command publishing |
| `MACHINE_MQTT_BROKER_URI` | blank | TLS broker URI, for example `ssl://broker.example:8883` |
| `MACHINE_MQTT_USERNAME` | blank | Broker service username |
| `MACHINE_MQTT_PASSWORD` | blank | Broker service password |
| `MACHINE_MQTT_CLIENT_ID` | `machine-service-ingestion` | Stable MQTT client identifier; keep it unchanged across restarts to resume the broker session |
| `MACHINE_COMMAND_EXPIRY_INTERVAL_MS` | `60000` | Interval for expiring overdue pending/acknowledged commands |
| `MACHINE_MONITOR_INTERVAL_MS` | `15000` | Device health polling interval |
| `MACHINE_UNSTABLE_AFTER_MS` | `30000` | Age of the last heartbeat before status becomes `UNSTABLE` |
| `MACHINE_OFFLINE_AFTER_MS` | `90000` | Age of the last heartbeat before status becomes `OFFLINE` |
| `MACHINE_ALERT_ESCALATE_AFTER_MS` | `300000` | Time before an unresolved alert is escalated |
| `MACHINE_LOW_STOCK_PERCENT` | `15` | Telemetry percentage at or below which a low-stock alert is opened |
| `MACHINE_INTEGRATION_SHARED_SECRET` | blank | Required shared key for the internal low-stock integration endpoint |
| `MACHINE_HISTORY_RETENTION_DAYS` | `90` | Retention period for telemetry and device event history |
| `MACHINE_HISTORY_RETENTION_CRON` | `0 20 3 * * *` | Daily schedule for bounded history cleanup |

In PowerShell, for example:

```powershell
$env:MACHINE_DB_USERNAME = "root"
$env:MACHINE_DB_PASSWORD = "your-local-password"
```

From this directory (`services/machine-service`), start the service:

```powershell
mvn spring-boot:run
```

The API is available at `http://localhost:8084` by default. Stop it with `Ctrl+C`.

`spring.jpa.hibernate.ddl-auto=update` lets Hibernate update mapped tables during local development; it does not create the MySQL database itself. Create the database first with the SQL script. The SQL file is not run automatically by Maven or Spring so schema initialization remains an explicit operator action.

## Tests

Run the integration tests from this directory:

```powershell
mvn clean test
```

Tests start the Spring application on a random port and use an in-memory H2 database. They do not require MySQL and do not touch the development database.

Application source is under `src/main/java/com/scentstation`, resources such as `application.properties` are under `src/main/resources`, and tests are under `src/test/java/com/scentstation`. This is Maven's standard Java project layout, so the POM uses Maven's default source, resource, and test directories. The Java packages are organized by responsibility:

```text
src/main/java/com/scentstation/
├── MachineServiceApplication.java
├── controller/       REST endpoints
├── dto/
│   ├── request/      API request models
│   └── response/     API response models
├── entity/           JPA entities and domain enums
├── event/            Spring application events
├── exception/        API exceptions and error handling
├── repository/       Spring Data repositories
└── service/          Business logic and operating-hours policy

src/main/resources/
└── application.properties

src/test/
├── java/com/scentstation/
│   ├── MachineServiceIntegrationTestBase.java
│   ├── *Test.java
│   └── *IntegrationTest.java
└── resources/
    └── application-test.yml
```

`AvailabilityRulesTest` tests the public `OperatingHours` policy from the same `com.scentstation` test package as the integration tests. `MachineServiceIntegrationTestBase` is their shared abstract fixture. Integration tests activate the `test` profile and load their H2 in-memory database settings from `src/test/resources/application-test.yml`. This keeps test configuration separate from the MySQL settings used to run the service. Maven includes `src/test/resources` on the test classpath automatically.

Maven writes generated build output to `target/` in this service directory. It contains compiled classes, test reports, and the packaged JAR; it is generated by Maven and is not source code. The repository `.gitignore` excludes `target/`, so do not add or upload this folder. To remove generated output and rebuild from scratch, use the clean test command above.

The packaged JAR is created under `target/` when you run `mvn package` or `mvn install`.

## Main API routes

All routes return or accept JSON.

| Method | Route | Purpose |
|---|---|---|
| `POST` | `/api/locations` | Create a location |
| `GET` | `/api/locations?tenantId={tenantId}` | List locations for a tenant |
| `GET` | `/api/locations/{locationId}` | Get a location |
| `PUT` | `/api/locations/{locationId}` | Update a location without changing its tenant |
| `PATCH` | `/api/locations/{locationId}/active?active=false` | Activate or deactivate a location |
| `POST` | `/api/machines` | Register a machine |
| `GET` | `/api/machines?tenantId={tenantId}` | List machines for a tenant |
| `GET` | `/api/machines/{machineId}` | Get a machine |
| `PUT` | `/api/machines/{machineId}` | Update machine details and assignment |
| `PATCH` | `/api/machines/{machineId}/device-registration-status` | Activate or revoke device registration |
| `PATCH` | `/api/machines/{machineId}/operational-status` | Update operational status |
| `PATCH` | `/api/machines/{machineId}/control` | Enable, disable, or enter maintenance |
| `PUT` | `/api/machines/{machineId}/operating-hours` | Set operating hours |
| `POST` | `/api/machines/{machineId}/heartbeat` | Record an internal heartbeat; requires `X-Internal-Service-Key` |
| `GET` | `/api/machines/{machineId}/availability` | Get machine and slot availability |
| `GET` | `/api/machines/{machineId}/history` | Get machine history |
| `POST` | `/api/machines/{machineId}/slots` | Create a slot |
| `GET` | `/api/machines/{machineId}/slots` | List slots |
| `GET` | `/api/machines/{machineId}/slots/{slotId}` | Get a slot |
| `PUT` | `/api/machines/{machineId}/slots/{slotId}` | Update slot configuration |
| `PATCH` | `/api/machines/{machineId}/slots/{slotId}/enabled` | Enable or disable a slot |
| `PATCH` | `/api/machines/{machineId}/slots/{slotId}/status?status={status}` | Report a slot status |

For example, after starting the service, check a machine's availability with:

```http
GET http://localhost:8084/api/machines/1/availability
```

Register a machine only after creating a location for the same tenant. Requests that change machine or slot data may include an optional `X-Actor-Id` header; it is recorded in history and is not an authentication mechanism.

## MQTT device contract

MQTT is disabled unless `MACHINE_MQTT_ENABLED=true`. When enabled, startup requires an `ssl://` broker URI and non-empty service credentials. The broker must present a trusted TLS certificate. Use a unique MQTT username and password for every device. The device username must be exactly the topic's unpadded Base64 URL-safe `deviceToken`; do not share the machine-service ingestion account with devices. Configure broker ACLs that bind `%u` to that one device identity. For Mosquitto-style ACLs, the device role should be restricted to:

```text
pattern write scentstation/v1/devices/%u/heartbeat
pattern write scentstation/v1/devices/%u/telemetry
pattern write scentstation/v1/devices/%u/events
pattern write scentstation/v1/devices/%u/commands/+/ack
pattern write scentstation/v1/devices/%u/commands/+/result
pattern read  scentstation/v1/devices/%u/commands
```

The `MACHINE_MQTT_USERNAME`/`MACHINE_MQTT_PASSWORD` belong to a separate machine-service broker account, never a device. Give that account read access to `scentstation/v1/devices/+/heartbeat`, `/telemetry`, `/events`, and `/commands/+/ack` plus `/commands/+/result`, and write access only to `scentstation/v1/devices/+/commands`. Confirm ACL substitution, deny rules, TLS certificate validation, and credentials using the actual broker's configuration and an integration test before enabling MQTT in production. The application also checks that the `deviceIdentity` in JSON matches the encoded topic and the active registered machine; this application check is defense in depth, not a substitute for broker-side per-device authentication.

Device uplink Topics use the form `scentstation/v1/devices/{deviceToken}/{heartbeat|telemetry|events}`. `deviceToken` is the unpadded Base64 URL-safe encoding of the UTF-8 `deviceIdentity` (for example, `machine-001` becomes `bWFjaGluZS0wMDE`). Device messages are JSON, QoS 1, and include `eventId`, the original `deviceIdentity`, `tenantId`, and ISO-8601 `occurredAt`. A durable receipt table makes each event ID unique across all devices and message types; exact retries from the same device/type are ignored. Example telemetry:

```json
{
  "eventId": "evt-7821",
  "deviceIdentity": "machine-001",
  "tenantId": "tenant-12",
  "occurredAt": "2026-10-25T12:00:00Z",
  "slotId": 41,
  "perfumeLevelPercent": 12.5,
  "perfumeWeightGrams": 35.2,
  "doorOpen": false,
  "leakDetected": false,
  "sensorStatus": "OK",
  "actuatorStatus": "OK",
  "powerStatus": "OK",
  "firmwareVersion": "2.4.1",
  "configurationVersion": "config-7"
}
```

`telemetry` can additionally include `commandId`, `slotId`, perfume level/weight, door/leak state, sensor/actuator/power status, and firmware/configuration versions. `events` uses `eventType` values such as `SENSOR_ERROR`, `ACTUATOR_ERROR`, `DISPENSE_FAILED`, `SLOT_EMPTY`, `LOW_STOCK`, `DOOR_OPEN`, `LEAK_DETECTED`, `COMMAND_ACK`, and `COMMAND_RESULT`; a failed command result sets `success` to `false`. The service rejects unregistered/revoked devices, mismatched tenant or topic identities, invalid measurements, and duplicate telemetry/event IDs. Heartbeat retries with the same `eventId` are ignored.

Commands are published to `scentstation/v1/devices/{deviceToken}/commands` with QoS 1 and a non-retained payload containing `commandId`, `deviceIdentity`, `tenantId`, `commandType`, `parameters`, `issuedAt`, `expiresAt`, and `source`. The publishing service verifies the registered active device and tenant, commits a `PENDING` command record, then publishes only after the transaction succeeds. If publishing fails after commit, the record remains pending and is marked `EXPIRED` by the command-expiry job when its deadline passes. The device reports an acknowledgement on `scentstation/v1/devices/{deviceToken}/commands/{commandId}/ack` and its result on `scentstation/v1/devices/{deviceToken}/commands/{commandId}/result`; both are validated against the command record and payload ID, update command lifecycle state, and are stored as device events. `MACHINE_COMMAND_EXPIRY_INTERVAL_MS` controls how often overdue `PENDING` and `ACKNOWLEDGED` commands are expired. No public HTTP command endpoint is provided by this batch.

The MQTT subscriber uses a persistent session (`cleanSession=false`) and QoS 1 subscriptions so the broker can redeliver unacknowledged messages after reconnect. Keep `MACHINE_MQTT_CLIENT_ID` stable and unique to this service instance, and configure the broker to retain persistent sessions and queued QoS 1 messages for that client. This supports reconnect recovery but does not replace a durable application-level outbox for command publication.

The monitor only considers machines that have sent at least one heartbeat. It changes active registered machines to `UNSTABLE` or `OFFLINE` based on the configurable heartbeat ages, opens one offline alert on transition, and resolves it when heartbeat returns. It reads bounded batches and locks only a candidate machine while rechecking its latest heartbeat. Telemetry and device-event history endpoints require the machine's `tenantId`, support `from`/`until` ISO-8601 filters and `page`/`size`, and default to 100 records per page (maximum 500). A daily cleanup job retains the latest configured period, deleting at most 5,000 old rows per table per run.

## Alert API

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/api/alerts?tenantId={tenantId}&status={status}&severity={severity}` | List tenant alerts, optionally filtered |
| `GET` | `/api/alerts/{alertId}?tenantId={tenantId}` | Get an alert within the supplied tenant |
| `POST` | `/api/alerts/{alertId}/acknowledge?tenantId={tenantId}` | Acknowledge an alert (`actorId`) |
| `POST` | `/api/alerts/{alertId}/assign?tenantId={tenantId}` | Assign an alert (`assigneeId`) |
| `POST` | `/api/alerts/{alertId}/resolve?tenantId={tenantId}` | Resolve an alert (`actorId`, optional `notes`) |
| `POST` | `/api/alerts/{alertId}/reopen?tenantId={tenantId}` | Reopen a resolved alert (optional `notes`) |
| `POST` | `/api/alerts/integration/low-stock` | Accept a low-stock alert from an internal service |
| `GET` | `/api/machines/{machineId}/telemetry?tenantId={tenantId}&from={instant}&until={instant}&page=0&size=100` | Filter and page telemetry history |
| `GET` | `/api/machines/{machineId}/device-events?tenantId={tenantId}&from={instant}&until={instant}&page=0&size=100` | Filter and page device event history |

The low-stock integration request requires the `X-Internal-Service-Key` header matching `MACHINE_INTEGRATION_SHARED_SECRET`. Alert responses include first/last seen timestamps, acknowledgement/assignment/resolution state, reopen count, response time, and resolution time. Active alerts are deduplicated by machine, alert type, and optional slot.

## Availability rules

A machine is available when its device registration is `ACTIVE`, its operational status is `ONLINE`, and the current time in `MACHINE_OPERATING_HOURS_ZONE` is within its operating hours. The default zone is Vietnam time (`Asia/Ho_Chi_Minh`), independent of the server or container's zone. If both operating-hour values are `null`, there is no time restriction; send both as `null` to the operating-hours endpoint to clear a schedule. Send both times or neither—only one `null` value is rejected.

A slot is available only when the machine is available, the slot is enabled and has status `AVAILABLE`, and both `productId` and `cartridgeId` are assigned. The availability response includes a per-slot `availabilityReason` when a slot is unavailable, as well as the machine-level reason.

Spray-duration requests are capped at 5,000 ms. Treat this as a conservative API limit, not as proof that a real dispenser is safe: validate it against the hardware manufacturer's specifications and test the calibration on the actual device before deployment.

After a machine's device registration is revoked, its slot configuration and status cannot be changed. A machine in `DISABLED` state must be re-enabled through the control endpoint; directly setting it to `ONLINE` or another non-disabled status is rejected.

## Integration limitations

- Authentication and role-based access control are not implemented. Do not expose management endpoints publicly; integrate with the shared Identity/API Gateway security contract before production.
- `tenantId`, `productId`, and `cartridgeId` are identifiers owned by other services. Tenant IDs are accepted from requests for local development; production tenant scope should come from authenticated identity.
- The service stores product and cartridge IDs but does not connect to Catalog to confirm that a cartridge contains the product assigned to a slot. That validation requires an agreed Catalog lookup/API contract.
- The HTTP heartbeat endpoint is restricted to trusted internal callers by `X-Internal-Service-Key`; it resolves an active offline alert when it restores the machine. Production devices should use MQTT and broker-side per-device authentication/ACLs as described above.
- Alert changes publish a local Spring `MachineAlertChangedEvent` only after the database transaction commits, with event ID, tenant, machine, location, alert type, severity, status, time, and source. There is no durable outbox or remote Notification/Maintenance adapter until those services' final event contract and transport are agreed.
- The low-stock integration secret is a shared-key boundary, not a replacement for service-to-service identity, rotation, or network policy. Alert management routes also require protection by the existing API gateway/identity layer before production.
- Any active machine-level alert blocks machine availability; an active slot-level alert blocks that slot. Downstream ordering services must use the availability result before accepting a dispense order.
