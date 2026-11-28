# ScentStation

ScentStation is a university monorepo for an IoT smart perfume kiosk system.

The local backend stack runs the API Gateway and seven Spring Boot services (eight backend components total), including Notification.
Each business service has a separate MySQL database and communicates with other services via HTTP.

```powershell
Copy-Item .env.example .env
# Replace JWT_SECRET, INTERNAL_SERVICE_KEY and REPORTING_SYNC_API_KEY in .env.
docker compose up -d --build
pwsh -File infra/demo-data/seed-demo.ps1
pwsh -File scripts/Test-ReportingIntegration.ps1
```

The Gateway is available at `http://localhost:8080`. See the
[Reporting Service guide](services/reporting-service/README.md#local-integration)
for dashboard, payment synchronization, and replay.
Vietnamese integration notes: [Tích hợp Reporting](docs/reporting-integration.md).
Shared Redis setup, cache boundaries, and verification steps are in the
[Redis guide](docs/redis/README.md).
The [Identity and Platform guide](docs/identity-platform.md) includes the one-command PowerShell demo and verification.
Kafka topics, outbox guarantees, DLT handling and remaining synchronous boundaries
are documented in [Kafka integration](docs/kafka-integration.md).

## Architecture

- SOA and microservices
- Docker-based local infrastructure
- API Gateway in front of backend services
- Java 21 LTS and Spring Boot for backend services
- React for frontend applications
- MySQL 8.4 with one database per business service
- Redis for cache and distributed rate limiting
- Apache Kafka for machine alerts, durable payment projections and inventory-consumption events
- Firebase Cloud Messaging as an optional notification delivery adapter
- Mock QR payment web for the local payment flow
- Prometheus, Alertmanager and Grafana for opt-in observability
- Apache NiFi for the documented Order-to-Reporting ETL workflow

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
| `notification-service` | Tenant/user notification inbox and delivery adapters | TBD |

## Frontend

- `frontend/admin-web`: admin and operation web application.
- `frontend/kiosk-web`: kiosk web application.
- `frontend/mock-payment-web`: mock payment page used by QR code flows on customer phones.

Frontend applications must call backend capabilities through the API Gateway.

## Runtime technology status

| Technology | Current status |
|---|---|
| Java 21, Spring Boot, Spring Security, JPA | Running in backend services |
| React, TypeScript, Vite | Running in Admin, Kiosk and Mock Payment web apps |
| MySQL | Running as isolated databases for each data-owning service |
| Redis | Running for caches and rate limits |
| Kafka | Running for machine alerts, Order-to-Reporting payment events, and Order-to-Operation dispense events; retries and DLTs are configured |
| Firebase Cloud Messaging | Implemented; enabled only when local credentials are supplied |
| QR payment | Implemented with the local Mock Payment Web, not a real provider |
| Prometheus/Alertmanager/Grafana | Metrics, dashboard and authenticated alerts to Notification Service through the opt-in `monitoring` profile |
| MQTT/Mosquitto | Not implemented; no broker or device adapter runs in Compose |
| Apache NiFi | Runtime container and JDBC integration available; flow setup is currently manual |

## Device

- `device/simulator` and `device/firmware` are reserved workspaces only; no executable simulator or firmware is currently present.

When hardware integration is implemented, Machine Service remains the only backend service allowed to communicate directly with devices.

## Data Ownership Rules

- Each backend service owns its own data boundary.
- A service must not access another service's database directly.
- Cross-service integration must go through agreed contracts.
- No shared business entity or shared domain model is defined at this stage.

## Infrastructure Notes

- Redis is an infrastructure component, not a business service.
- Kafka and Redis are shared infrastructure components, not business services.
- The local Compose stack includes seven MySQL databases, Redis, Kafka, the API Gateway and seven business services.
- Firebase credentials remain local and must never be committed.
- Monitoring instructions are in [docs/monitoring.md](docs/monitoring.md).
- NiFi runs with `infra/nifi/start.ps1`; credentials stay in the ignored `infra/nifi/.env` and `verify.ps1` checks its driver/network wiring.

## Not Implemented Yet

- Real payment-provider integration
- MQTT/device execution and telemetry
- Automatically versioned/provisioned NiFi flow definition
