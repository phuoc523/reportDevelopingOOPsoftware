# Operation Service

Operation Service is an independent ScentStation microservice for operational workflows. It owns its own `operation_db` database and must not directly access another service's database or create cross-service foreign keys.

Modules:

- Inventory
- Alert
- Maintenance

Inventory is implemented on this branch and currently includes:

- Batch
- Bottle / Cartridge
- Installation
- Removal and atomic replacement
- Refill
- Adjustment
- Sensor reconciliation and remaining-spray estimation
- Idempotent customer/diagnostic dispense consumption
- Tenant-scoped movement and local audit history

References to data owned by other services use scalar identifiers such as `productId`, `machineId`, and `slotId`; Operation does not define their entities.

`MachineSlotClient` is the Machine integration boundary. Its simulator is available only in the `local` and `test` profiles. A production/default deployment must provide a real adapter.

Alert and Maintenance remain future modules and are not implemented by the Inventory stabilization work.

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
