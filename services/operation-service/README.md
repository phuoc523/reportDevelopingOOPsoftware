# Operation Service

Operation Service is an independent ScentStation microservice for operational workflows. It owns its own `operation_db` database and must not directly access another service's database or create cross-service foreign keys.

Planned modules:

- Inventory
- Alert
- Maintenance

Member 2 is currently responsible only for Inventory. The planned Inventory scope includes:

- Batch
- Bottle / Cartridge
- Installation
- Refill
- Adjustment

References to data owned by other services will use identifiers such as `productId`, `machineId`, and `slotId`; this bootstrap does not define those fields or entities.

Business logic has not been implemented yet.

## Environment Variables

- `OPERATION_DB_URL` (default: `jdbc:mysql://localhost:3306/scentstation_operation`)
- `OPERATION_DB_USERNAME`
- `OPERATION_DB_PASSWORD`
- `OPERATION_SERVICE_PORT` (default: `8085`)

## Run with Maven

```bash
mvn spring-boot:run
```

## Run Tests

```bash
mvn clean test
```

Tests use an H2 in-memory database and do not require a local MySQL instance.

## Build

```bash
mvn clean package
```

## Build Docker Image

```bash
docker build -t scentstation/operation-service .
```
