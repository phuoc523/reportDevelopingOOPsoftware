# ScentStation Java Device Simulator

Standalone Java 21 MQTT v1 simulator for the four-slot demo kiosk. It connects as a device identity, publishes heartbeat/telemetry/events/alerts and consumes only its own command topic. Machine Service remains the authority for machine safety, command state and reservation validation.

## Protocol behavior

- MQTT 3.1.1, QoS 1, clean session, no retained messages.
- Uses the configured numeric `machines.id` in topic and envelope. The username must map to that machine in the Mosquitto ACL.
- Accepts the existing `DISPENSE_COMMAND` envelope and its `commandId`, `slotId`, `durationMs`, and `expiresAt`. Product assignment and dose are local simulator configuration because MQTT v1 intentionally does not include those fields.
- Emits only the contract's `COMMAND_RESPONSE` statuses (`ACKNOWLEDGED`, `DISPENSED`, `FAILED`, `EXPIRED`, `UNKNOWN`). It does not send `DISPENSE_SUCCESS` or `DISPENSE_FAILED` device events.
- Persists the command journal and simulated slot quantity before/after actuation. Duplicate command IDs replay the stored response and never run the actuator a second time. An `IN_PROGRESS` command found after a process restart becomes `UNKNOWN` and is not actuated again.
- A response publish future completing means the broker acknowledged QoS 1 delivery. MQTT v1 has no application-level backend-processing acknowledgement, so the simulator does not claim that MySQL or Operation Service has processed the response. Mark an end-to-end kiosk run successful only after the backend command result is `DISPENSED` and the expected inventory movement/stock update is visible.

Telemetry `weightGrams` is derived from the configured simulated remaining volume, dose, density and empty bottle weight. It is diagnostic only; it does not update backend inventory. Stock deduction remains driven by the existing validated backend result/event flow and reservation.

## Four-slot demo profile

`config/demo-9101.json` maps machine `9101`'s four seeded slot IDs to local product IDs and simulated bottle quantities. These are a virtual prototype profile, not a database seed. If the database slot mapping changes, update this simulator configuration to match it. Slot IDs and product IDs are validated as canonical numeric IDs.

The simulator requires all network/authentication settings from environment variables:

| Variable | Required/default | Purpose |
|---|---|---|
| `SIMULATOR_BROKER_URI` | Required | `tcp://host:1883` or `ssl://host:8883` |
| `SIMULATOR_MACHINE_ID` | Required | Numeric machine ID used in MQTT topics |
| `SIMULATOR_MQTT_USERNAME` | Required | Device username configured in broker ACL |
| `SIMULATOR_MQTT_PASSWORD` | Required | Device's distinct broker password |
| `SIMULATOR_SLOT_CONFIG` | `config/demo-9101.json` | Slot/product/dose/quantity profile |
| `SIMULATOR_STATE_FILE` | `data/device-state.json` | Durable command journal and simulated slot state |
| `SIMULATOR_MODE` | `NORMAL` | Startup fault mode |
| `SIMULATOR_HEARTBEAT_INTERVAL_MS` | `5000` | Heartbeat interval |
| `SIMULATOR_TELEMETRY_INTERVAL_MS` | `5000` | Telemetry interval |
| `SIMULATOR_RECONNECT_INTERVAL_MS` | `5000` | Connection retry interval |
| `SIMULATOR_DURATION_SCALE` | `1.0` | Multiplier for command `durationMs`; tests may use `0` |

Do not reuse the Machine Service password as a device password. Local broker passwords are provisioned by the existing Mosquitto setup profile and kept in the ignored root `.env` file.

## Run as an independent process

From the repository root, set environment values in the current PowerShell session without placing secrets in command history:

```powershell
$env:SIMULATOR_BROKER_URI = 'tcp://localhost:1883'
$env:SIMULATOR_MACHINE_ID = '9101'
$env:SIMULATOR_MQTT_USERNAME = 'device-ss-demo-9101'
$env:SIMULATOR_MQTT_PASSWORD = '<value from the ignored .env file>'
$env:SIMULATOR_SLOT_CONFIG = 'device-simulator/config/demo-9101.json'
$env:SIMULATOR_STATE_FILE = 'device-simulator/data/device-state.json'
mvn -f device-simulator/pom.xml package
java -jar device-simulator/target/device-simulator-1.0.0-SNAPSHOT.jar
```

The simulator is a separate JVM and does not depend on Machine Service being in the same process. Backend services and the broker must be running for an integrated kiosk session.

## Optional local Compose profile

The optional profile starts one 4-slot virtual kiosk for demo machine 9101 and stores its journal in a named volume:

```powershell
docker compose --profile mqtt-setup run --rm mosquitto-setup
docker compose up -d mqtt-broker machine-service
docker compose --profile device-simulator up --build device-simulator-9101
```

Use `docker compose --profile device-simulator logs -f device-simulator-9101` to observe the device. The profile is opt-in; default Compose startup does not add a simulator.

## Fault controls

In an interactive terminal, enter one command per line:

```text
status
mode DOOR_OPEN
mode LEAK
mode SENSOR_ERROR
mode MAINTENANCE
mode NORMAL
slot 91001 empty
slot 91001 fill 50
slot 91001 sensor ERROR
slot 91001 sensor NORMAL
network down
network up
quit
```

`network down` stops heartbeat/telemetry by disconnecting the MQTT client; `network up` reconnects and republishes fresh telemetry. The simulator never attempts a spray while the door, leak, machine sensor, maintenance or selected slot sensor is unsafe. If a fault appears during the simulated actuation window, the response is `UNKNOWN`, because the physical outcome cannot be asserted safely. Device-originated critical leak/maintenance alerts are not automatically cleared by normal telemetry; the existing backend alert lifecycle remains in control.

## Kiosk and end-to-end validation

The existing `frontend/kiosk-web` already provides the customer catalog/order/payment/dispensing screens. Point `VITE_KIOSK_MACHINE_ID` at `9101` and use the normal local identity, payment and backend stack. The flow is:

1. Customer selects a fragrance slot and submits the trial order/payment through the existing kiosk APIs.
2. Backend validates payment, product/slot, safety and active stock reservation, then Machine Service publishes `DISPENSE_COMMAND`.
3. The simulator performs the configured virtual dose and sends a v1 command response.
4. Machine Service validates and persists the response; Order Service stores `DISPENSED` plus its existing outbox event; Operation Service consumes the event and applies the reservation-backed inventory transaction.
5. Verify the final order/command status and inventory movement through the existing service APIs/database before recording the demo as successful. Broker PUBACK alone does not prove those application steps completed.

This module includes unit tests and a Mosquitto Testcontainers round-trip test. The round-trip test proves wire-level MQTT behavior, not backend/MySQL processing. A physical pump, sensors, wiring, enclosure and kiosk hardware were not present in the repository/environment; this deliverable is the four-slot virtual kiosk prototype and its integration profile, not a built physical appliance.
