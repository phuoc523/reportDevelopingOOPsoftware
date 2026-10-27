# ScentStation Identity & Platform — P1-06 to P1-11

**Basis**: Java 21, Spring Boot 3.5.6, opaque token + RBAC + TenantScope from P1-03/P1-04/P1-05. This bundle is a *candidate implementation*, NOT an attested production deployment. Execute verification on the actual project and record Jira evidence before marking Done.

## Scope / acceptance / evidence

| ID | Delivered in patch | Verify before Jira Done | External dependency |
|---|---|---|---|
| P1-05 | Reuse previous TenantScope and introspection | Live API verification + cross-service DB tests | Catalog/Order/Inventory/Machine/Reporting owners |
| P1-06 | JPA platform_settings, global/tenant APIs, safe allowlist, validation, defaults, tests | PlatformModulesIntegrationTest + DB persistence restart | Agreement on keys/limits |
| P1-07 | Standalone Spring Cloud Gateway WebFlux, optional routes, restrictive CORS, request ID, internal blocking, health, contract tests | GatewayContractTest + live forwarding to each *confirmed* service | Ports and actual API prefix from owners |
| P1-08 | Contract in this document: auth, service-to-service, tenant, correlation/secret | Team sign-off and sample requests | All service owners |
| P1-09 | Immutable API-shaped audit event store, privileged read and service-key-protected append, tests | Internal audit POST + scoped GET + DB retention/perms | Refund/inventory/maintenance event producers |
| P1-10 | Service-key-protected notification ingestion, tenant+event idempotency, role routing, in-app inbox, tests | In-app demo; duplicate, wrong tenant and bad key | Machine Alert event producer |
| P1-11 | Tests and scripts; docs, sample handover and status matrix | Maven suites + live Gateway + tenant + Docker Compose E2E | Group's Compose/services readiness |

### Do not claim full E2E Done yet

Identity only guarantees tenant scope for its own data. Each downstream service must verify opaque tokens with an *authenticated internal call*, enforce validated tenant on every repository read/update/delete, and test Tenant A vs Tenant B. A user-provided tenant header is never an authorization grant. An authorized platform admin can explicitly select tenant according to policy; cross-tenant reads by ordinary staff fail closed.

## P1-06 config APIs

- `GET /api/v1/platform-config/global/{key}`: BRAND_ADMIN or PLATFORM_SUPER_ADMIN read global override/default.
- `PUT /api/v1/platform-config/global/{key}`: PLATFORM_SUPER_ADMIN only; JSON `{"value":"120"}`.
- `GET /api/v1/platform-config/tenants/{tenantId}/{key}`: own tenant brand admin or platform admin. Response `{scopeId,key,value,source}` where source is `DEFAULT`, `GLOBAL_OVERRIDE`, `TENANT_OVERRIDE`.
- `PUT /api/v1/platform-config/tenants/{tenantId}/{key}`: own tenant brand admin or platform admin.
- Values: `DEVICE_IDLE_TIMEOUT_SECONDS` (30–3600; default 300), `ORDER_PAYMENT_TIMEOUT_SECONDS` (30–3600; default 300), `MACHINE_HEARTBEAT_TIMEOUT_SECONDS` (10–600; default 60), `NOTIFICATION_ENABLED` (`true|false`; default true), `NOTIFICATION_CHANNEL` (`IN_APP`; default IN_APP).
- Invalid key/value: HTTP 400; wrong tenant/role: HTTP 403; anonymous: HTTP 401.
- Do NOT put passwords, API tokens, payment keys, or personally sensitive data in this table. Defaults are demo defaults; align with business owners before production. Schema created by JPA `ddl-auto=update` **only in development**. Production requires versioned migrations.

## P1-07 Gateway

- Module: `services/api-gateway/`; Spring Cloud `2025.0.3` for Boot 3.5 (Gateway 4.3.5).
- Gateway port `8080` by default; health at `/actuator/health`.
- Only `IDENTITY_URL` is prefilled (`http://127.0.0.1:8081`). `CATALOG_URL`, `OPERATION_URL`, `ORDER_URL`, `MACHINE_URL`, `REPORTING_URL` **default to disabled**. Don't set them until actual owner-approved ports and paths are known.
- Public form `/api/v1/{service}/...` maps to downstream `/api/v1/...`. E.g. `/api/v1/identity/auth/login` -> `IDENTITY_URL/api/v1/auth/login`. Confirm this rule matches each downstream service before enabling it.
- Gateway rejects any path containing `/internal/` and strips untrusted `X-Verified-*` and `X-Internal-Service-Key` from external calls. It supplies a server-generated `X-Request-Id`; forwards `Authorization` for backend checks.
- CORS only from exact comma-separated `CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`), no wildcard.
- Gateway is **not** a trust authority: each downstream backend must validate session/tenant. Internal endpoints still need network restrictions and their own secrets. Never expose them publicly or rely on Gateway alone for internal protection.
- Backend outage: request returns a 5xx; verify actual error contract with the group. No fake fallback 200 responses.

## P1-08 verified security contract

1. External client sends `Authorization: Bearer <opaque-token>`; any `tenantId` in path/query/body/header is merely a claimed target, **never** source of truth.
2. Backend authenticates each request, derives `userId`, `tenantId` and `roles` from the validated session using Identity's internal `/api/v1/internal/tenant/introspect` endpoint. Only trusted workloads, with `X-Internal-Service-Key: <TENANT_INTROSPECTION_KEY>` via private network, can call that endpoint.
3. Downstream validates permission **and** resource tenant in its repository query/update (e.g. `WHERE id=? AND tenant_id=?`); simply checking after an unscoped fetch is not enough.
4. Use `X-Request-Id` generated by Gateway for tracing. Never include bearer tokens, login passwords, service keys, payment secrets or full raw request bodies in logs.
5. `401` unauthenticated/invalid token, `403` authenticated wrong permission or tenant, `404` missing or cross-tenant resource (to avoid disclosing existence), `400` invalid config/input, `503` missing required secret configuration, gateway route `404` if unconfigured.
6. Service keys (`TENANT_INTROSPECTION_KEY`, `AUDIT_SERVICE_KEY`, `NOTIFICATION_SERVICE_KEY`) must be independently random ≥32 characters, stored in secret manager / environment, rotated, not shared in Git/Jira and never passed by frontend.
7. Do not accept caller-supplied `X-Verified-Tenant-Id` etc from the internet. Do not expose internal endpoints from Gateway. Firewall/network isolation must be applied in Docker/production.

**Internal introspection sample** (from trusted backend only, never frontend):
```
POST http://identity-service:8081/api/v1/internal/tenant/introspect
X-Internal-Service-Key: <server-secret>
Content-Type: application/json
{"accessToken":"<user-opaque-token>"}
```
Response `{userId,username,tenantId,roles,permissions}`. Validate token on backend before scope decisions; do not persist user access tokens.

## P1-09 shared audit API

- Internal POST `/api/v1/internal/audit/events` header `X-Internal-Service-Key: <AUDIT_SERVICE_KEY>` + body `{"tenantId":"...","actorId":"...","action":"REFUND_APPROVED","resourceType":"ORDER","resourceId":"...","outcome":"SUCCESS","requestId":"..."}`.
- Only trusted authenticated service identities may submit; validate actor and tenant from verified context *in the emitting service* (server key alone signifies trust in caller, not human user identity). Network-isolate endpoint.
- `GET /api/v1/audit/events?tenantId=...` staff Bearer; requires `ROLE_AUDIT_READ` RBAC, tenant scoped. Default max 50. Keep existing P1-04 role-assignment audit endpoint unchanged.
- No API update/delete. **Production** must configure separate DB privilege to prevent unauthorized UPDATE/DELETE, audit retention/backup and independent log export. Log IDs/status only, never full payload/secret.
- Producer integration (Auth, Role Grant/Revoke, Refund, Inventory Adjust, Machine Maintenance) is a *separate cross-service obligation*. Module receives events but does not magically intercept business operations in other services.

## P1-10 in-app notification API

- Internal POST `/api/v1/internal/notifications/events` header `X-Internal-Service-Key: <NOTIFICATION_SERVICE_KEY>` body:
```
{"tenantId":"TENANT_ID","eventId":"machine-001","eventType":"MACHINE_ALERT","title":"Demo alert","message":"Machine needs check","recipientRoles":["TECHNICIAN","BRAND_ADMIN"]}
```
- Role-based recipient lookup is scoped by tenant. Key `(tenantId,eventId)` is unique; duplicate returns `DUPLICATE_IGNORED`. The `DELIVERED_IN_APP` status means saved to a user's inbox, **not** email/push delivery.
- Staff GET `/api/v1/notifications/me` with bearer token, returns only own tenant + own user deliveries, up to 50.
- `NO_RECIPIENTS` is returned if no eligible staff account matched. Channel `IN_APP` only; SMTP, retry queues, rate-limiting, distributed deduplication and external channel delivery are not part of the current demo.
- Machine Alert module owns detection; this module only accepts an event from it.

## P1-11 acceptance and evidence

Execute both Maven test suites with Java 21, save logs, and run `scripts/P1-11-GATEWAY-SMOKE.ps1` once identity+gateway are running. Also run existing `services/identity-service/scripts/verify-p1-05-live.ps1` with MySQL and `DEMO_ADMIN_PASSWORD` configured. Capture evidence in `E:\XD\Platform-Evidence`, without exposing tokens or secrets. Run manual end-to-end cases with Tenant A/B seeded by each service owner:

- Staff Tenant A auth succeeds; no bearer returns 401, wrong permission 403.
- Spoofed header/path/body Tenant B denied; repo refuses read/update/delete B resource, no row changed.
- Authorized Tenant A config PUT then GET persists across restart; invalid value 400; B denied.
- Trusted internal audit POST appears only for permitted tenant; wrong key 401.
- Trusted notification event delivers to designated in-app accounts; duplicate not redelivered; B cannot view A's delivery.
- Confirmed Gateway route forwards to real service; CORS allow / disallow; unknown route 404; internal route 404; backend outage yields 5xx.
- Logs have correlation ID but no credentials.

Only change Jira subtasks to **Done** after actual passing test evidence and owner sign-off. Track unavailable service routes or Compose integration as **Blocked** with responsible owner, not fictitious completion.
