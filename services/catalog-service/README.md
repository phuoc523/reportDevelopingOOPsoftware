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

`HeaderCurrentActorContext` is a temporary integration adapter. Its `X-Verified-User-Id`, `X-Verified-Tenant-Id`, and `X-Verified-Roles` headers are trustworthy only when Gateway strips client values, recreates them from authenticated Identity data, and the deployment prevents direct external access to Catalog. Catalog does not authenticate opaque tokens itself.

Canonical tenant identifier must be resolved during integration phase with Identity/Gateway owners: Identity currently uses UUID strings while Catalog persists positive `Long` tenant IDs.

## Machine Availability Boundary

`MachineAvailabilityClient` is the integration abstraction. `LocalMachineAvailabilityClient` is a local/test simulator only and is enabled only by the `local` or `test` Spring profile. It always returns one available slot and must not be treated as real Machine integration.

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

- Gateway/Identity trusted-header integration and UUID-versus-`Long` tenant reconciliation;
- real Machine availability adapter;
- Product price and Campaign audit-event contract;
- endpoint-level RBAC policy.

Catalog must eventually emit price/campaign audit events, while Reporting owns the searchable central audit projection. No shared team audit contract currently exists, so Catalog does not invent one here.

## Run with Maven

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
