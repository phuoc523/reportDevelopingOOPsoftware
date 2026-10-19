# ScentStation

ScentStation is a university monorepo for an IoT smart perfume kiosk system.

Current scope: repository structure only. Business logic is not implemented yet.

## Architecture

- SOA and microservices
- Docker-based local infrastructure
- API Gateway in front of backend services
- Java 25 LTS and Spring Boot for backend services
- React for frontend applications
- MySQL, Redis, Apache NiFi, and Mosquitto as infrastructure components
- Device Simulator before real hardware is available

## Backend Services

| Service | Expected responsibility | Owner |
|---|---|---|
| `api-gateway` | Single backend entry point and routing boundary | TBD |
| `identity-service` | Identity-related scope, not implemented yet | TBD |
| `catalog-service` | Catalog-related scope, not implemented yet | TBD |
| `order-service` | Order-related scope, not implemented yet | TBD |
| `machine-service` | Machine and device-facing scope, not implemented yet | TBD |
| `operation-service` | Operation-related scope, not implemented yet | TBD |
| `reporting-service` | Reporting-related scope, not implemented yet | TBD |

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
- MySQL, Mosquitto, Redis, NiFi, and Docker configuration are only skeletons until the team finalizes ports, environment variables, and runtime details.

## Not Implemented Yet

- Business logic
- Business entities
- Database tables or migrations
- API design
- Business controllers or services
- JWT, authentication, authorization, or payment
- MQTT logic
- NiFi flows
