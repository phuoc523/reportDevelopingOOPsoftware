# Redis available to all ScentStation backend services

## Scope

The shared Redis 7.4 instance is connected to API Gateway, Identity, Catalog, Order,
Machine, Notification, Operation and Reporting. Each Spring Boot backend includes
Spring Data Redis and Spring Cache. The cache manager uses a 60-second default TTL,
does not cache null values, and applies a distinct service prefix. The React frontends
continue to call backend APIs and do not connect to Redis directly.

Redis-backed business caches are enabled for tenant-scoped Catalog product lists,
Identity tenant profile fields, and Machine location lists. Keys include the tenant
identifier where applicable, and writes evict the affected cache entries. Gateway,
Order, Notification, Operation, and Reporting retain Redis connectivity and a cache
manager but do not cache volatile workflow or authorization data.

Identity caches profile fields only. Tenant status is read live on every profile
request so a suspension is not hidden by a cache hit. Authentication decisions,
tokens, sessions, RBAC results, and revocation state are never cached. Cached DTOs
must remain serializable for Redis.

## Run full local stack

From repository root:

```powershell
docker compose up -d --build
```

Compose waits for Redis health before starting each backend. Redis is published only
on localhost port 6379. Check it with:

```powershell
docker compose exec redis redis-cli ping
```

## Run a backend outside Compose

Start only Redis:

```powershell
docker compose up -d redis
```

Set `REDIS_HOST=127.0.0.1`, `REDIS_PORT=6379`, and optionally `REDIS_PASSWORD` in the
backend environment. Cache TTL is configurable using `REDIS_CACHE_TTL` (default
`60s`); set `REDIS_CACHE_PREFIX` to the service name plus colon. For service data,
include tenant identity in the Spring cache key and define update/delete invalidation.

The local Compose Redis has no password and is intended only for development. Use
network restrictions, authentication and TLS in shared or deployed environments.
