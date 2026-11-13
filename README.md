# ScentStation

ScentStation is a university monorepo for an IoT smart perfume kiosk system.

The local backend stack runs the API Gateway and six Spring Boot services.
Each business service has a separate MySQL database and communicates with other services via HTTP.

```powershell
docker compose up -d --build
pwsh -File scripts/Test-ReportingIntegration.ps1
```

The Gateway is available at `http://localhost:8080`. See the
[Reporting Service guide](services/reporting-service/README.md#local-integration)
for dashboard, payment synchronization, and replay.
Vietnamese integration notes: [Tích hợp Reporting](docs/reporting-integration.md).

## Architecture

- SOA and microservices
- Docker-based local infrastructure
- API Gateway in front of backend services
- Java 21 LTS and Spring Boot for backend services
- React for frontend applications
- MySQL, Redis, Apache NiFi, and Mosquitto as infrastructure components
- Device Simulator before real hardware is available

## Backend Services

| Service | Expected responsibility | Owner |
|---|---|---|
| `api-gateway` | Single backend entry point and routing boundary | TBD |
| `identity-service` | Authentication, user administration, roles and audit | TBD |
| `catalog-service` | Products, availability, pricing and campaigns | TBD |
| `order-service` | Orders, simulated QR payments and dispensing requests | TBD |
| `machine-service` | Machines, locations, slots, alerts and dispensing commands | TBD |
| `operation-service` | Inventory and maintenance | TBD |
| `reporting-service` | Transactions, dashboards and Excel exports | TBD |

## Frontend

- `frontend/admin-web`: admin and operation web application.
- `frontend/kiosk-web`: kiosk web application.

Frontend applications must call backend capabilities through the API Gateway.

## Device

- `device/simulator`: simulator used before real hardware is available.
- `device/firmware`: firmware workspace for real device work.

Machine Service is the only backend service allowed to communicate directly with MQTT devices.

## Data Ownership Rules

- Each backend service owns its own data boundary.
- A service must not access another service's database directly.
- Cross-service integration must go through agreed contracts.
- No shared business entity or shared domain model is defined at this stage.

## Infrastructure Notes

- Redis is an infrastructure component, not a business service.
- Apache NiFi is an infrastructure component, not a business service.
- The local Compose stack includes MySQL and the backend services.

## Not Implemented Yet

- Real payment-provider integration
- MQTT logic
- NiFi flows
