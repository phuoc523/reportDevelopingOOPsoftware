# Contributing

This repository is initialized for parallel development by five members.

## Working Rules

- Work in the folder owned by your task.
- Do not commit secrets.
- Do not access another service's database directly.
- Do not add business logic before the team agrees on requirements and contracts.
- Do not introduce new infrastructure technology without team approval.
- Keep API, MQTT, NiFi, and database contracts documented before implementation.

## Suggested Ownership

| Member | Main scope |
|---|---|
| Member 1 | API Gateway and Identity Service |
| Member 2 | Catalog Service and related contracts |
| Member 3 | Order Service |
| Member 4 | Machine Service and Device workspace |
| Member 5 | Frontend and Reporting Service |
