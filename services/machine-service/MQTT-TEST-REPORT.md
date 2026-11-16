# MQTT Communication & Device Authentication — Test Report

## Assessment

**PARTIALLY DONE** — implementation and regression tests are present, but broker authentication and ACL behavior could not be verified in this environment because the Docker Engine is unavailable.

## Executed verification

| Command/check | Result |
|---|---|
| Baseline `mvn -f services\machine-service\pom.xml test` | PASS — 25 tests, 0 failures, 0 errors |
| `mvn -f services\machine-service\pom.xml -Dtest=MqttDeviceMessageValidatorTest test` | PASS — 9 tests, 0 failures, 0 errors |
| `mvn -f services\machine-service\pom.xml clean test` | PASS — 40 total, 34 passed, 0 failures, 0 errors, 6 skipped |
| `docker compose -f services\machine-service\compose.mqtt.yml --profile mqtt config` | PASS — Compose configuration parsed |
| Opt-in Mosquitto test: `mvn -f services\machine-service\pom.xml '-Dmachine.mqtt.broker-tests=true' -Dtest=MosquittoAclIntegrationTest test` | SKIPPED — all 6 broker tests skipped; Docker could not connect to `npipe:////./pipe/dockerDesktopLinuxEngine` |
| `git diff --check` | PASS — no whitespace errors |

The six skipped tests are broker-level checks and are not counted as verified security outcomes.

## Security test status

| Scenario | Actual result |
|---|---|
| Anonymous broker connection denied | NOT VERIFIED — broker unavailable |
| Invalid password denied | NOT VERIFIED — broker unavailable |
| Registered device publishes to its own allowed topic | NOT VERIFIED against broker |
| Device A denied access to Device B's topic | NOT VERIFIED against broker |
| Device cannot publish to command topic | NOT VERIFIED against broker |
| Backend can publish to its authorized test topic | NOT VERIFIED against broker |
| Retained and oversized messages rejected | NOT VERIFIED against broker |
| Unknown/revoked device, mismatched identity, schema, timestamp, payload size, duplicate ID | PASS in validator unit tests |
| Tenant derived from Machine record, not message payload | PASS in validator unit tests |

## Known limitations

- Broker credentials are provisioned out of band; Machine registration does not automatically create Mosquitto users.
- Static password/ACL configuration does not automatically kick already-connected clients. Revocation requires removing the broker account and restarting Mosquitto.
- MQTT 3.1.1 does not expose the authenticated publisher username in subscriber callbacks. Device-to-topic identity is enforced by broker ACLs; the backend independently validates the registered active Machine and topic/envelope identity. The JSON payload itself is not a cryptographic proof of publisher identity.
- The local Compose listener is loopback-only and has no TLS. Production deployments must configure and verify a TLS broker listener and client truststore.
- Duplicate-message tracking is bounded and in-memory, not durable across service restarts.
- No heartbeat, telemetry, event, ACK/result business handling or physical dispensing behavior is implemented.

## Evidence locations

- Maven reports: `target/surefire-reports/`
- Local broker configuration: `compose.mqtt.yml`, `mqtt/mosquitto.conf`, `mqtt/acl`
- Contract and setup instructions: `README.md`
- Opt-in broker integration tests: `src/test/java/com/scentstation/mqtt/MosquittoAclIntegrationTest.java`

Before marking P4-04 fully accepted, start Docker Desktop and rerun the opt-in Mosquitto security test command in the README. A passing run is still required to claim broker-level authentication/authorization acceptance.
