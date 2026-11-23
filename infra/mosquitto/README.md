# Local MQTT broker

The local Mosquitto broker and its device ACLs are configured for MQTT
protocol v1, documented in [contracts/mqtt/v1.md](../../contracts/mqtt/v1.md).
Machine Service uses MQTT 3.1.1 to publish command messages and receive
response, heartbeat, telemetry, event, and alert messages. The Java simulator
has not been implemented yet.

The demo machine identities are the MQTT usernames:

| Machine | Username |
|---|---|
| 9101 | `device-ss-demo-9101` |
| 9102 | `device-ch-demo-9102` |
| 9103 | `device-dr-demo-9103` |

Each device and the Machine Service have distinct passwords in the ignored root
`.env`. The ACL maps each device username to its numeric machine ID. The
Machine Service has command-publish and inbound-subscribe access. Each device
receives commands only from its own topic and can publish only to its own
response/heartbeat/telemetry/event/alert topics. Mosquitto may acknowledge
a cross-device subscription at QoS 0, but its ACL prevents delivery of messages
from that topic.

The broker listener is plain MQTT on host `127.0.0.1:1883` by default and is
also reachable to containers on the Compose network as `mqtt-broker:1883`.
It is restricted to local development; do not expose this listener to an
untrusted network. Production must use TLS (`ssl://` broker URI), trusted broker
certificates, and production credential provisioning. The local broker has no
TLS.

Machine Service settings are passed through Compose:

| Variable | Default | Purpose |
|---|---|---|
| `MACHINE_MQTT_ENABLED` | `true` in Compose | Enables the Machine Service MQTT client |
| `MACHINE_MQTT_BROKER_URI` | `tcp://mqtt-broker:1883` | Local broker URI; use `ssl://host:8883` for a TLS endpoint |
| `MQTT_MACHINE_SERVICE_PASSWORD` | Required in `.env` | Machine Service's broker password; never commit the value |
| `MACHINE_MQTT_OFFLINE_TIMEOUT_MS` | `15000` | Heartbeat/telemetry timeout before marking an online machine offline |
| `MACHINE_MQTT_PENDING_COMMAND_SCAN_MS` | `5000` | Retry interval for accepted commands until expiry or device response |

## Initialize credentials and start

Set four different strong values for `MQTT_MACHINE_SERVICE_PASSWORD` and the
three `MQTT_DEVICE_*_PASSWORD` variables in the root `.env`. Then run:

```powershell
docker compose --profile mqtt-setup run --rm mosquitto-setup
docker compose up -d mqtt-broker
```

The setup command copies the broker configuration and ACL to a Docker volume
with restricted permissions, and writes only Mosquitto password hashes to a
separate Docker volume. Plain-text passwords remain in the ignored `.env`.

## Verify device ACLs

Run the repeatable local broker/ACL check:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\infra\mosquitto\test-broker.ps1
```

It verifies authenticated command/response delivery, invalid-password
rejection, retained-message rejection, and that device-to-device ACL rules
prevent cross-device message delivery.

Machine Service subscribes at QoS 1 to the five device-to-backend wildcard
topics and publishes non-retained commands at QoS 1. Accepted commands are
republished with the same `messageId` and `commandId` until a response arrives
or the command expires; devices must therefore deduplicate by `commandId`.
