# Catalog Service

Catalog Service is the ScentStation service boundary for catalog data.

Planned scope:

- Product
- Location
- Campaign

Business logic has not been implemented yet.

## Run with Maven

```bash
mvn spring-boot:run
```

Default configuration:

- Application name: `catalog-service`
- Port: `8082`
- Datasource placeholders: `CATALOG_DB_URL`, `CATALOG_DB_USERNAME`, `CATALOG_DB_PASSWORD`

Database and JPA auto-configuration are disabled in this skeleton so the service can start before MySQL schema and business entities exist.

## Run Tests

```bash
mvn clean test
```

## Build Docker Image

```bash
docker build -t scentstation/catalog-service .
```
