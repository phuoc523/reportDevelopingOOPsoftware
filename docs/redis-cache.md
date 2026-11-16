# Redis cache — Identity & Platform

**Branch:** `feature/redis-cache` (based on `integrationfullstack`).

## Scope and security

Caches JSON tenant-display snapshots only for:

- `GET /api/platform/tenants/{id}`
- `GET /api/brand/tenant`

All existing controller authorization rules run before the cache is accessed. The brand route resolves tenant ID from `AccessPolicy.current()` and calls `TenantService.requireActive()` using the live database before reading the cache. JWT authentication, RBAC, tokenVersion, and tenant activation are **never** decided using Redis.

Key: `scentstation:identity:v1:tenant-views:tenant:<tenantId>`

Default TTL: **60 seconds**. Missing tenants and null results are not cached. Successful platform profile updates, brand profile updates, and status changes schedule eviction of **only** their tenant key in an `afterCommit` transaction callback. A rolled-back transaction does not trigger eviction.

A narrow race between concurrent GET and write can briefly repopulate an older display snapshot; bounded by the TTL. This is not an authorization source.

## Local Redis (PowerShell, repository root)

```powershell
docker compose -f docker-compose.redis.yml up -d
docker compose -f docker-compose.redis.yml exec redis redis-cli ping
$env:REDIS_HOST = "127.0.0.1"
$env:REDIS_PORT = "6379"
$env:IDENTITY_CACHE_ENABLED = "true"
$env:IDENTITY_CACHE_TTL = "60s"
mvn -f services/identity-service/pom.xml spring-boot:run
```

Keep current MySQL and security environment variables from your existing setup. Root `.env` does not get loaded automatically by Maven. Compose binds Redis to localhost only, with no persistence. For shared deployments use authenticated Redis over TLS and restrict network access. Do not expose this development Compose instance publicly.

By default `IDENTITY_CACHE_ENABLED=false`. When disabled, reads go straight to MySQL. When enabled, Redis becomes a required dependency and failures are surfaced: there is no automatic fallback or durable invalidation retry.

## Manual API verification with existing authenticated users

1. Read either GET endpoint, then confirm key creation and positive TTL.
2. Repeat GET and inspect tenant payload queries (authentication can still query MySQL).
3. PATCH profile, then GET; verify the updated display data.
4. Suspend tenant, then use its old JWT; its protected requests must be rejected even if a display view was cached.
5. Compare brand A and brand B: separate tenant keys and responses.
6. After the TTL expires, repeat GET and verify fresh data is loaded.

```powershell
docker compose -f docker-compose.redis.yml exec redis redis-cli --scan --pattern 'scentstation:identity:v1:tenant-views:tenant:*'
docker compose -f docker-compose.redis.yml exec redis redis-cli TTL scentstation:identity:v1:tenant-views:tenant:1
```

Do not flush unrelated keys.

## Automated verification

```powershell
mvn -f services/identity-service/pom.xml test
docker run --rm -d --name scentstation-redis-test -p 127.0.0.1:16379:6379 redis:7.4-alpine
$env:RUN_REDIS_TESTS = "true"
$env:REDIS_TEST_PORT = "16379"
mvn -f services/identity-service/pom.xml test
docker stop scentstation-redis-test
Remove-Item Env:RUN_REDIS_TESTS
Remove-Item Env:REDIS_TEST_PORT
```

The seven opt-in Redis tests use IDs 900000001 and 900000002. **Never point them at production or shared Redis.** Local unit tests mock repositories; they are not a MySQL/HTTP end-to-end test.

## Operational limits

- Redis outage can cause cached reads or after-commit eviction to fail, including after a MySQL write has committed. Disable cache and restart Identity to read from MySQL while restoring Redis; allow old keys to expire before re-enabling.
- Concurrent GET and PATCH can transiently repopulate stale display data (at most TTL).
- Do not use display-cache data for auth, authorization, or active-tenant enforcement.
- Run the API/MySQL verification checklist in the team's deployment environment before declaring the Jira item Done.
- Verify current CI run and Maven test reports; no success result is assumed merely from pushing this source.
