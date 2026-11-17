# Shared Redis implementation test report

This report distinguishes Maven test success from real Redis verification. A skipped Testcontainers test is not a Redis integration pass.

## Scope and environment

- Worktree: `reportDevelopingOOPsoftware-redis-use-all-services-phung-latest`
- Branch: `feature/redis-use-all-services-phung`
- Base: `origin/integration/be-fe-technologies` at `9dc95af`
- Backend scope: 8 components (API Gateway plus Identity, Catalog, Order, Machine, Operation, Reporting, and Notification).
- Java available: 25.0.5; the repository's Docker build uses Java 21. Maven tests used `-Dmaven.compiler.proc=full` and `-DargLine=-Dnet.bytebuddy.experimental=true`.
- Docker Engine was unavailable (Docker Desktop Linux Engine pipe missing); `redis-server` and `redis-cli` were not installed locally.

## Maven test suites

| Component | Maven result | PASS | FAIL | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| API Gateway | PASS | 15 | 0 | 0 | 1 |
| Identity | PASS | 51 | 0 | 0 | 2 |
| Catalog | PASS | 84 | 0 | 0 | 5 |
| Order | PASS | 63 | 0 | 0 | 2 |
| Machine | PASS | 61 | 0 | 0 | 2 |
| Operation | PASS | 281 | 0 | 0 | 8 |
| Reporting | PASS | 177 | 0 | 0 | 9 |
| Notification | PASS | 80 | 0 | 0 | 1 |
| **Total** | **PASS** | **812** | **0** | **0** | **30** |

Each component was run with Maven `clean test`. Identity, Catalog, Machine, and Operation were rerun after adding unit tests that verify write-triggered cache invalidation occurs after transaction commit. All eight final suite results were successful.

## Redis and cache verification

| Behavior | Result | Evidence / limitation |
|---|---|---|
| Redis read/write, cache hit/miss, TTL expiry, Redis key isolation | BLOCKED | Testcontainers Redis integration tests are present but skipped because Docker was unavailable. No Redis PONG or live Redis key inspection was possible. |
| Invalidation after successful write | PASS (unit) | Identity, Catalog, Machine, and Operation tests verify the correct tenant or tenant/ticket cache eviction runs after commit using a mocked cache. Live Redis deletion was not verified. |
| Redis outage behavior | PASS (unit) | Redis-unavailable tests pass; Gateway, Reporting, and Notification rate limiters return service-unavailable responses, while cache clients surface Redis access failures. No live outage/recovery test was possible. |
| Gateway rate limiting | PARTIAL | Unit tests pass for limit behavior and unavailable responses. The Redis-backed counter integration test was skipped, so real Redis 429/expiry behavior was not verified. |
| Tenant/user isolation | PARTIAL | Keys and unit-level scope checks are service-scoped and tenant/user-specific. Redis-backed cross-tenant isolation tests were skipped. |

Configured TTLs: Spring `RedisCacheManager` default 60 seconds with null caching disabled and a distinct service prefix; Identity, Catalog, and Machine caches 60 seconds; Order status display metadata 5 minutes; Operation checklist display 5 seconds; Gateway, Reporting, and Notification rate-limit counters default to 1 minute (configurable). Redis-backed expiry behavior remains unverified.

## Compose and runtime

- Root Compose and standalone Catalog, Order, and Operation Compose files parsed successfully in an earlier configuration-only check; no containers were started.
- Final Compose parsing was not repeated after the last environment/required-secret edits. Treat final Compose validation as PARTIAL.
- Compose startup, service health checks, Redis `PONG`, and real Redis integration are BLOCKED by the unavailable Docker Engine.
