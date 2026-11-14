# Catalog Service

Catalog Service owns:

- Product master data: name, description, fragrance notes, image, catalog availability, and `defaultPrice` per spray;
- Campaigns using percentage or fixed-amount discounts;
- effective promotional pricing;
- product search/filter;
- the Catalog side of the Machine availability integration boundary.

`Product.defaultPrice` is the Catalog/default price per spray. It is not the actual Machine Slot selling price. Machine owns slot price/configuration; Order owns the immutable selected-price snapshot.

## Tenant Isolation Boundary

Catalog business services use `CurrentActorContext` as the trusted tenant source. Request body/query `tenantId` fields are compatibility claims only and cannot override the trusted tenant.

`IdentityCurrentActorContext` forwards the request Bearer token to private Identity introspection and uses the returned current principal. Caller-supplied identity headers have no authority. Catalog fails closed when Identity rejects the token, is unavailable, or returns malformed data.

## Machine Availability Boundary

`MachineAvailabilityClient` is the integration abstraction. `LocalMachineAvailabilityClient` is enabled only by the `local` or `test` Spring profile and deliberately fails closed with unavailable/zero slots. It must not be treated as real Machine integration.

Run locally with the simulator:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Real slot availability, slot selling price, connectivity, stock, maintenance state, and safe-dispense decisions remain owned by Machine/Operation services.

## Campaign Limitations

- Implemented: `PERCENTAGE` and `FIXED_AMOUNT` discounts.
- Future scope: explicit free-trial and temporary absolute-price campaigns.
- Current implementation policy: when campaigns overlap, the lowest effective price wins. This is not yet a stakeholder-approved requirement.

## Known Integration Gaps

- real Machine availability adapter;
- Product price and Campaign audit-event contract;
- platform Product detail/mutation APIs do not yet accept an explicit tenant target and therefore fail closed for null-tenant platform actors.

Catalog must eventually emit price/campaign audit events, while Reporting owns the searchable central audit projection. No shared team audit contract currently exists, so Catalog does not invent one here.

## Run with Maven

Requires JDK 25 or newer.

```bash
mvn spring-boot:run
```

Default configuration:

- Application name: `catalog-service`
- Port: `8082`
- Datasource placeholders: `CATALOG_DB_URL`, `CATALOG_DB_USERNAME`, `CATALOG_DB_PASSWORD`

Catalog uses JPA and the configured datasource for Product and Campaign persistence.

## Run Tests

```bash
mvn clean test
```

## Build Docker Image

```bash
docker build -t scentstation/catalog-service .
```
## Sample Data

Catalog sample data is disabled by default.

To enable it for local development, update:

```yaml
catalog:
  sample-data:
    enabled: true
```
