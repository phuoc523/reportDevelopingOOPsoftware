# ScentStation Machine Service

REST and MQTT infrastructure for machine, location, slot, availability, alerts, and dispensing commands.

## Database

Flyway applies `src/main/resources/db/migration/V1__initial_machine_schema.sql` on startup. Create the `scentstation_machine` database and configure its credentials before starting the service.

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
| MACHINE_MQTT_ENABLED | false | Enable MQTT client; REST starts without a broker when disabled |
| MACHINE_MQTT_BROKER_URL | tcp://localhost:1883 | MQTT broker URI (`ssl://` for a TLS listener) |
| MACHINE_MQTT_USERNAME | blank | Backend MQTT username; required when MQTT is enabled |
| MACHINE_MQTT_PASSWORD | blank | Backend MQTT password; required when MQTT is enabled |
| MACHINE_MQTT_CLIENT_ID | machine-service | Backend MQTT client ID |
| MACHINE_MQTT_CONNECTION_TIMEOUT_SECONDS | 10 | Broker connection timeout |
| MACHINE_MQTT_KEEP_ALIVE_INTERVAL_SECONDS | 30 | MQTT keep-alive interval |
| MACHINE_MQTT_RECONNECT_INTERVAL_MS | 5000 | Initial connection retry interval |
| MACHINE_MQTT_MAX_PAYLOAD_BYTES | 65536 | Application payload limit |
| MACHINE_MQTT_ALLOWED_CLOCK_SKEW_SECONDS | 300 | Accepted device timestamp skew |

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

## MQTT communication and device authentication

MQTT is disabled by default and does not perform heartbeat, telemetry, event, ACK, dispensing-result, or physical dispensing business processing. When enabled, the Paho client connects asynchronously, retries failed initial connections, reconnects after disconnects, and validates inbound envelopes. QoS 1 is used; consumers must tolerate duplicate delivery. MQTT v3.1.1 has no per-message expiry property in this implementation, so messages must not be interpreted as durable exactly-once events. Retained messages are disabled by the local broker and the service never publishes retained messages. The backend uses a clean session; reconnecting does not replay its old subscriptions or publish commands.

### Topic contract

| Topic | Publisher | Subscriber | QoS |
|---|---|---|---|
| `scentstation/v1/devices/{deviceId}/heartbeat` | That device | Machine Service | 1 |
| `scentstation/v1/devices/{deviceId}/telemetry` | That device | Machine Service | 1 |
| `scentstation/v1/devices/{deviceId}/events` | That device | Machine Service | 1 |
| `scentstation/v1/devices/{deviceId}/acks` | That device | Machine Service | 1 |
| `scentstation/v1/devices/{deviceId}/results` | That device | Machine Service | 1 |
| `scentstation/v1/devices/{deviceId}/commands` | Machine Service (ACL only; no application command publisher) | That device | 1 |
| `scentstation/v1/devices/{deviceId}/backend/test` | Machine Service | That device | 1 |

Messages use a versioned JSON envelope:

```json
{
  "schemaVersion": 1,
  "messageId": "a4433b62-f809-4e51-9a7e-f2bc880db120",
  "deviceId": "device-01",
  "type": "HEARTBEAT",
  "timestamp": "2026-11-14T05:00:00Z",
  "payload": { "status": "READY" }
}
```

Inbound channel types must match their topic (`HEARTBEAT`, `TELEMETRY`, `DEVICE_EVENT`, `COMMAND_ACK`, or `DISPENSE_RESULT`). The validator checks schema version, UUID message ID, topic/payload device identity, timestamp skew, payload size, active Machine registration, and duplicate message IDs. It derives Tenant from the registered Machine and never trusts a Tenant in the JSON payload. Duplicate tracking is in-memory and bounded; it is a validation foundation, not durable exactly-once processing.

`MachineMqttPublisher.publishConnectivityTest(deviceId)` publishes only to the separate `backend/test` topic with type `CONNECTIVITY_TEST`; it does not publish to `commands` and cannot trigger physical dispensing. No REST endpoint is added for this helper.

### Local broker setup

`compose.mqtt.yml` starts Mosquitto only on host loopback. It denies anonymous connections, disables retained publishes, enforces a 64 KiB message limit, and applies per-username topic ACLs. The backend username is `machine-service`; each device username must exactly match a registered `deviceIdentity` composed of letters, digits, `.`, `_`, or `-`. Provision credentials out of band; no real password belongs in this repository.

From `services/machine-service`, create the secret directory and use Mosquitto's password utility interactively (it prompts for the password):

```powershell
New-Item -ItemType Directory -Force mqtt\secrets | Out-Null
docker run --rm -it -v "$($PWD.Path)\mqtt\secrets:/mosquitto/config" eclipse-mosquitto:2.0.22 mosquitto_passwd -c /mosquitto/config/passwd machine-service
docker run --rm -it -v "$($PWD.Path)\mqtt\secrets:/mosquitto/config" eclipse-mosquitto:2.0.22 mosquitto_passwd /mosquitto/config/passwd device-01
docker compose -f compose.mqtt.yml --profile mqtt up -d
```

Add one password-file entry per registered device. The ACL pattern allows each username to write only its own heartbeat/telemetry/events/acks/results and read only its own commands and backend test topic. Devices cannot publish commands or access another device's topics. The backend can read device-originated channels and is the only configured identity allowed to publish commands or backend test messages. The backend client ID defaults to `machine-service`; device client IDs may follow `device-{deviceId}` for operations, but client IDs are labels and are not authentication evidence.

Rotate a device password by invoking the password tool against the same mounted secrets folder, then restart the broker:

```powershell
docker run --rm -it -v "$($PWD.Path)\mqtt\secrets:/mosquitto/config" eclipse-mosquitto:2.0.22 mosquitto_passwd /mosquitto/config/passwd device-01
docker compose -f compose.mqtt.yml --profile mqtt restart mosquitto
```

Revoke its broker account by deleting the password-file entry, then restart the broker to disconnect existing sessions and apply the change:

```powershell
docker run --rm -v "$($PWD.Path)\mqtt\secrets:/mosquitto/config" eclipse-mosquitto:2.0.22 mosquitto_passwd -D /mosquitto/config/passwd device-01
docker compose -f compose.mqtt.yml --profile mqtt restart mosquitto
```

Set `MACHINE_MQTT_ENABLED=true`, backend username/password, and (for local compose) `MACHINE_MQTT_BROKER_URL=tcp://localhost:1883` to enable the service client. Do not expose this unauthenticated-transport local listener outside the developer machine. For deployment, configure a broker TLS listener, trusted server certificate, and a Java truststore; use an `ssl://` broker URL and JVM truststore options. This repository's local broker configuration does not enable or verify TLS.

### Trust boundary and revocation

Mosquitto authenticates the username/password and enforces the ACL before accepting publish/subscribe operations. Its ACL binds a device username to that username's topic namespace. MQTT 3.1.1 does not expose the authenticated publisher username in a subscriber callback, so Machine Service does **not** claim to cryptographically authenticate the publisher from the JSON body. The backend additionally checks that the topic and envelope identify a known ACTIVE Machine and derives Tenant from that database record. This application check cannot compensate for a compromised broker or incorrectly provisioned broker ACL.

Machine registration does not automatically create broker credentials. On device revocation, set registration status to `REVOKED` and remove its broker password entry. The backend rejects subsequent messages after observing the database status. The bundled static Mosquitto password/ACL configuration has no dynamic session-kick integration: to guarantee that an already-connected client loses access, an operator must reload/restart Mosquitto after revocation (disconnecting existing clients). Immediate automated revocation of active broker sessions is therefore not implemented.

## Tests

```powershell
mvn test
```

Tests use the standard Maven layout and an isolated H2 database. MQTT message validation tests do not require a broker. The local Compose broker is a separate security test target; run broker authentication/ACL checks with credentials for two provisioned test devices before claiming broker-level acceptance. No broker-level test result is claimed by this implementation.

Run the opt-in broker security tests with Docker Desktop running:

```powershell
mvn '-Dmachine.mqtt.broker-tests=true' -Dtest=MosquittoAclIntegrationTest test
```

The test suite starts an isolated Mosquitto container with disposable test credentials and checks anonymous/invalid authentication, device topic isolation, command-topic restrictions, retained messages, and oversized payloads.
