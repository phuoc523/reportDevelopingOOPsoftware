# API Gateway Redis rate limiting

API Gateway uses `ReactiveStringRedisTemplate` and one atomic Lua script to implement a fixed-window counter. It does not use cache state to authenticate or authorize a request.

1. For a bearer token, the Gateway calls Identity Service's protected `/internal/auth/introspect` endpoint with its configured internal service key and a two-second timeout.
2. The returned verified tenant/subject identity is hashed with SHA-256 to form the Gateway-namespaced Redis key.
3. Missing or invalid tokens are limited by the direct socket peer IP; client-supplied identity headers and forwarded IP headers are not used.
4. The Lua script atomically increments the counter and sets expiry on the first increment. Requests over the configured limit receive `429` and `Retry-After`.
5. Redis errors or Identity verification outages fail closed with `503`; requests are not forwarded without the configured rate-limit decision.

`GATEWAY_RATE_LIMIT_REQUESTS_PER_MINUTE` defaults to 120 and `GATEWAY_RATE_LIMIT_WINDOW` defaults to `1m`. `OPTIONS`, `/actuator/health`, and `/api/auth/health` bypass the limiter so preflight and liveness checks continue to work. A production deployment should still put authentication/authorization at the owning services and may enforce additional edge/network limits.

Reporting and Notification also use short-lived Redis counters for their authorized read APIs. They derive tenant/user scope from the verified Identity response and do not cache dashboard, notification, alert, or delivery payloads.

## Verify

Set a low request limit for local testing, authenticate through the Gateway, and repeat requests within one window. The request exceeding the threshold should return 429. Use `docker compose exec redis redis-cli --scan --pattern "scentstation:gateway:v1:rate-limit:*"` to inspect counters without exposing identity values.

Stopping Redis should result in 503 for limited routes; it must not disable authentication or silently allow traffic through.
