# Verification procedure

## Automated service tests

Run all eight backend Maven test suites from the repository root:

```powershell
$services = @(
  "api-gateway",
  "identity-service",
  "catalog-service",
  "order-service",
  "machine-service",
  "operation-service",
  "reporting-service",
  "notification-service"
)
foreach ($service in $services) {
  mvn -f "services/$service/pom.xml" test
  if ($LASTEXITCODE -ne 0) { throw "Tests failed for $service" }
}
```

Redis integration tests use Testcontainers and require a working Docker engine. Unit tests cover the explicit Redis-unavailable behavior. Integration tests assert cache miss/hit, TTL expiration, tenant-key isolation, eviction, and Gateway/Reporting/Notification rate counting. When Docker is unavailable, Maven may mark Testcontainers tests skipped; do not report those as passed.

## Compose validation and runtime

```powershell
docker compose --env-file .env.example config --quiet
docker compose up -d --build
docker compose ps
docker compose exec redis redis-cli ping
```

For Catalog, Order, or Operation standalone Compose:

```powershell
docker compose --env-file .env.example -f services/catalog-service/docker-compose.yml config --quiet
docker compose --env-file .env.example -f services/order-service/compose.yml config --quiet
docker compose --env-file .env.example -f services/operation-service/docker-compose.yml config --quiet
```

Then verify an authorized read twice, inspect the resulting namespaced key and remaining TTL, update its source data, and verify eviction. For tenant isolation, repeat for two tenants and ensure each key/value is scoped independently. See the [cache matrix](cache-matrix.md) for keys and per-cache TTLs.

## Manual acceptance checklist

- Redis replies `PONG` and all eight backends become healthy.
- Dockerized services use `redis`; host-run services use `localhost`.
- No port is published for Redis.
- Keys have the expected `scentstation:{service}:v1:` prefix and tenant segment when the payload is tenant data.
- Cache expiry yields a miss and causes a source read.
- Update/delete invalidates the appropriate key after transaction commit.
- Different tenants cannot retrieve one another's cache entries.
- Stopping Redis causes explicit request/health failure; Gateway does not fail open.
- Reporting and Notification dashboard/inbox responses are never served from Redis; their Redis data contains only rate-limit counters.
- Payment, auth, suspension, inventory movement, dispensing, and device safety remain sourced from their owning services.
