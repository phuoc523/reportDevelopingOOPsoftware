# Redis failure behavior

The application does not turn a Redis exception into a cache miss. Cache operations currently propagate connection/serialization failures so callers do not receive a success-shaped response based on assumed cache state. The cache contents are not used for authentication, tenant status, payment, inventory decisions, device safety, or dispensing.

| Failure | Expected behavior | Recovery |
|---|---|---|
| Redis not reachable during app startup | Spring Boot still starts, but Actuator Redis health reports DOWN where exposed; Compose keeps the backend unhealthy and does not start the Gateway until its backend dependencies are healthy. | Restore Redis and wait for health checks. |
| Redis fails during cached read/write | The request fails explicitly rather than silently returning a potentially stale or cross-tenant result. | Restore Redis; retry safe reads. For writes, inspect the authoritative database because an after-commit cache eviction can fail after the database transaction has already committed. |
| Redis fails in Gateway limiter | Gateway returns `503` for limited routes. It never interprets the outage as permission to forward. | Restore Redis. Health and preflight paths remain exempt. |
| Identity introspection unavailable | Gateway returns `503` for bearer-token identity resolution, rather than trusting client-provided tenant/user headers. | Restore Identity connectivity and verify the internal service key. |
| Redis fails in Reporting or Notification rate limiter | The authenticated dashboard/inbox request returns `503`; no business payload is read from or written to Redis. | Restore Redis; retry the read. |
| Rate counter exceeds its limit | Gateway, Reporting, or Notification returns `429`; Redis stores only the short-lived counter. | Wait for the configured window to expire. |
| Redis restarts and loses cache keys | Cache reads miss and repopulate from the owning service. Rate-limit counters restart, so use Redis persistence and upstream network/edge controls if rate-counter continuity is required. | Check Redis logs/health; allow data to repopulate. |

Because Redis eviction can fail after a source database commit, only short-lived, non-authoritative display data is cached. Identity status is queried live; dashboard access fields are regenerated; Reporting dashboards and Notification payloads are not cached; payment and device/inventory decision paths never use a cache. Redis outages can reduce availability, but must not weaken authorization or safety controls.

To simulate an outage locally:

```powershell
docker compose stop redis
docker compose ps
docker compose start redis
docker compose ps
```

Do not remove Compose volumes as a recovery measure. Cache flags can be disabled for controlled local debugging, but the Gateway Redis-health check and Redis-dependent service health checks still require Redis to recover before the full Compose stack is healthy.
