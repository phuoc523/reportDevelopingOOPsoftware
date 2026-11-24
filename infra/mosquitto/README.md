# Local MQTT broker

Root `docker-compose.yml` runs Mosquitto 2 with authentication, retained messages disabled and a per-machine ACL. The canonical protocol is documented in [`contracts/mqtt/v1.md`](../../contracts/mqtt/v1.md).

## Credentials

Set two different passwords of at least 32 characters in the ignored root `.env`:

```text
MQTT_MACHINE_SERVICE_PASSWORD=<backend password>
MQTT_DEVICE_9101_PASSWORD=<ESP32 password>
```

Compose generates the Mosquitto password file in container tmpfs. Plain-text passwords are not written into the repository or a Docker volume.

## Start

```powershell
docker compose up -d mqtt-broker
```

The broker is available to backend containers at `mqtt-broker:1883` and to the ESP32 at `<laptop-LAN-IP>:1883`. Do not use `localhost` in ESP32 firmware. Port 1883 is exposed for local hardware testing; production must use TLS and production credential provisioning.

## ACL

- `machine-service` publishes `scentstation/+/command` and reads the five inbound channels.
- `device-ss-demo-9101` reads only `scentstation/9101/command` and publishes only machine 9101 inbound channels.
- Web/mobile clients never receive MQTT credentials.

Run the repeatable broker check after the container becomes healthy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\infra\mosquitto\test-broker.ps1
```

The script verifies command/response delivery, cross-machine isolation, invalid-password rejection and retained-message rejection.
