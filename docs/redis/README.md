# Shared Redis for ScentStation

The root `docker-compose.yml` runs one Redis 7.4 instance shared by all eight backend components: API Gateway and seven Spring Boot services, including Notification. Redis has no published host port and is attached only to the internal `scentstation-redis` network. The backends also stay on `scentstation-backend` for their existing HTTP and MySQL connections.

## Start the full stack

From the repository root in PowerShell:

```powershell
Copy-Item .env.example .env
# Edit .env and set strong local values for INTERNAL_SERVICE_KEY and JWT_SECRET.
docker compose up -d --build
docker compose ps
```

Set `REDIS_PASSWORD` in `.env` to enable Redis authentication. Leave it empty for an unauthenticated local Redis. Do not publish port 6379. The Compose Redis health check and each backend's health check must pass before the Gateway starts.

To start only Redis and its network first:

```powershell
docker compose up -d redis
docker compose ps redis
```

The standalone Catalog, Order, and Operation Compose files join the networks created by the root Compose project. Start the root Redis/network first; they do not create another Redis instance:

```powershell
docker compose -f services/catalog-service/docker-compose.yml up -d
docker compose -f services/order-service/compose.yml up -d
docker compose -f services/operation-service/docker-compose.yml up -d
```

Standalone files still require their service-specific database environment values and existing external network(s). The root Compose stack is the supported all-services local setup.

## Run a service outside Docker

Start Redis and that service's MySQL database, then set the Redis host to `localhost` in the service process environment. For example:

```powershell
$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6379"
$env:REDIS_PASSWORD = ""
$env:REDIS_CONNECT_TIMEOUT = "2s"
$env:REDIS_TIMEOUT = "2s"
mvn -f services/catalog-service/pom.xml spring-boot:run
```

Also provide the selected service's database and integration environment variables. Dockerized backends must use `REDIS_HOST=redis`, not `localhost`. Standalone Compose uses `REDIS_CONTAINER_HOST=redis`; the host-run `REDIS_HOST=localhost` default is not appropriate inside a container.

## Verify connection, keys, and TTL

Redis is intentionally not exposed on the host. Use `docker compose exec`:

```powershell
docker compose exec redis redis-cli ping
docker compose exec redis redis-cli --scan --pattern "scentstation:*"
docker compose exec redis redis-cli TTL "scentstation:catalog:v1:product-list:tenant:1"
```

With password protection, pass `-a <password>` to `redis-cli` without putting the password in shell history. A `TTL` result of `-2` means no key exists, `-1` means no expiry, and a positive number is the remaining lifetime in seconds. Application keys should have a positive TTL.

To demonstrate a cache hit, call the same authorized read twice and compare service/database query logs; to test isolation, use two authenticated tenants and verify the corresponding distinct keys. Update a cached item and verify its key is deleted after commit. Focused integration tests cover hit/miss, expiry, key isolation, and eviction.

## Configuration and cache scope

The connection properties are `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_CONNECT_TIMEOUT`, and `REDIS_TIMEOUT`. `REDIS_CACHE_TTL` sets the Spring `RedisCacheManager` default to 60 seconds; null values are disabled and every service has a distinct cache prefix. Business caches and rate counters use the per-use-case TTLs in [the cache matrix](cache-matrix.md). The default host is `localhost` for processes outside Docker. See [key design](key-design.md), [Gateway limiting](gateway.md), and [failure behavior](failure-modes.md).

## Stop and recover

```powershell
docker compose logs redis
docker compose restart redis
docker compose ps
```

Do not delete Redis data or MySQL volumes as a first recovery step. Redis is a disposable cache/rate-limit store; restart it and let cache entries expire or repopulate. The exact test commands and current verification limits are in [verification](verification.md) and [the test report](test-report.md).
