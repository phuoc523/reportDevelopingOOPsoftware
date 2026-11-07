# ScentStation P1 – Identity / RBAC patch for Hoàng Đình Phùng

**Status: implementation patch, not a claim that all P1-01…P1-11 are DONE.**
Reviewed against branch `test-2026-11-07-latest`, commit `1779c95`. The installation script refuses to overwrite modified baseline Java files. This patch changes **Identity Service**, the **Identity route in API Gateway**, and this documentation; it does not replace the other team members' services.

## Implemented scope

- Tenant/brand creation, profile, suspension/reactivation API and administrative visibility (P1-01 partial).
- Tenant-bound accounts, configurable multi-role assignment, account lock/disable, password reset, scope assignment, explicit permission grants (P1-02/P1-04 partial).
- JWT signature/expiry check + fresh account state, roles and `tokenVersion` lookup per request; logout and administrative changes revoke existing tokens (P1-03 partial).
- Backend checks for platform and brand administrative endpoints; tenant, location and machine scope policy object (P1-04/P1-05 partial).
- Identity audit events for creation, modifications, role/grant/status changes, login, logout (P1-09 partial).
- Identity route expansion in existing Gateway config for `/api/platform/**`, `/api/brand/**`, `/api/audit/**` (P1-07 partial).
- Unit tests for role matrix, tenant-scoped policy and token-version bumps; adapted original Spring security controller tests.

**Not implemented by this patch:** tenant enforcement within Catalog/Order/Machine/Inventory/Reporting, secure device identity and internal service headers, payment provider verification, Gateway JWT enforcement, production-grade audit event delivery, platform configuration, Notifications, production database migrations, and full deployment/end-to-end verification. These remain separate P1 tasks / inter-service integration dependencies.

## Roles and permission model

| Role | Selected baseline capabilities | Additional approval required |
|---|---|---|
| PLATFORM_SUPER_ADMIN | All platform + brand capabilities | Full authority; use monitored accounts |
| BRAND_ADMIN | Tenant profile, brand users, catalog, location/machine operations, reports, audit | REFUND_APPROVE, INVENTORY_ADJUST, DIAGNOSTIC_SPRAY, MACHINE_CONFIGURE |
| OPERATIONS_MANAGER | Machine read/control, review transactions, request refund, alerts, tickets | Refund approval and machine reconfiguration |
| TECHNICIAN | Assigned machines, maintenance, alerts | DIAGNOSTIC_SPRAY |
| INVENTORY_STAFF | Catalog, inventory/refill, alerts | INVENTORY_ADJUST |
| VIEWER | Scoped read-only data | Any mutation |
| CUSTOMER | Kiosk browse/order permissions | Management APIs |
| ADMIN / STAFF | Legacy-only endpoints; **no automatic tenant/platform privilege** | Migration required |

For brand users, per-user additional grants are stored in `user_permission_grants`. Only a **platform administrator** can grant high-risk permissions; brand administrators can grant a safe subset within their own tenant. Every change bumps `tokenVersion` and is audited. Location and machine scopes are available to backend policy checks; actual business services must consume/enforce the shared contract before the platform is considered fully isolated.

## Available endpoints (Identity Service :8081)

| Method + path | Access |
|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | Public, register always CUSTOMER |
| `GET /api/auth/me`, `POST /api/auth/logout` | Authenticated |
| `GET/POST /api/platform/tenants` | Platform administrator |
| `GET/PATCH /api/platform/tenants/{id}` | Platform administrator |
| `PATCH /api/platform/tenants/{id}/status` | Platform administrator |
| `GET/PATCH /api/brand/tenant` | Brand administrator, own brand only |
| `GET/POST /api/platform/users` | Platform administrator |
| `GET/POST /api/brand/users` | Brand administrator, own brand |
| `PATCH /api/{platform|brand}/users/{id}/roles` | Admin in own allowed scope |
| `PATCH /api/{platform|brand}/users/{id}/grants` | Platform or strictly limited brand administrator |
| `PATCH /api/{platform|brand}/users/{id}/status` | Admin in own allowed scope |
| `PATCH /api/{platform|brand}/users/{id}/scopes` | Admin in own allowed scope |
| `POST /api/{platform|brand}/users/{id}/reset-password` | Admin in own allowed scope |
| `GET /api/audit/recent?tenantId=...` | Platform all / brand own tenant with AUDIT_READ |

Tenant profile create example:

```json
{"code":"brand_demo","name":"Demo Brand","profile":{"logoUrl":"https://example.org/logo.png","description":"Demo","contactEmail":"support@example.org","contactPhone":"+84000000000","kioskContent":"Welcome"}}
```

Create first brand administrator (platform token required):

```json
{"username":"brand_admin_1","email":"admin1@example.org","password":"REPLACE_WITH_STRONG_PASSWORD","fullName":"Brand Admin","tenantId":1,"roles":["BRAND_ADMIN"],"locationIds":[],"machineIds":[]}
```

Grant a high-risk permission (platform admin only):

```json
{"permissions":["DIAGNOSTIC_SPRAY"]}
```

Tenant status update:

```json
{"status":"SUSPENDED"}
```

## Bootstrap platform administrator

Do **not** make public registration able to assign `PLATFORM_SUPER_ADMIN`. For the first administrator, set these variables **locally** for the initial startup, with a unique secret password, and then remove them:

- `PLATFORM_BOOTSTRAP_USERNAME`: unique platform admin username
- `PLATFORM_BOOTSTRAP_EMAIL`: valid email
- `PLATFORM_BOOTSTRAP_PASSWORD`: strong password, at least 16 characters

Bootstrap only creates the account if username does not already exist. Use BCrypt. There is **no default admin password**. Ensure no one exposes bootstrap values in screenshots/logs or commits. For live systems, adopt a vetted admin-onboarding or recovery procedure.

## Tenant suspension policy: not fully decided

The service stores `ACTIVE`/`SUSPENDED` and blocks brand-side changes and new brand accounts when suspended. **It does not yet block all external service purchases, dispensings, late payment webhooks, or refunds.** Those decisions require a signed-off cross-service policy and implementations owned by Order/Machine. Do not claim `Tenant suspended` business policy has passed end-to-end acceptance.

## Database considerations

- New `brand_tenants`, `user_roles`, `user_permission_grants`, `user_location_scopes`, `user_machine_scopes`, `identity_audit_events` entities/tables.
- New columns on `users`: `tenant_id`, `locked`, `token_version`; legacy `role` retained.
- Existing `ADMIN/STAFF/CUSTOMER` rows can remain; old privileged accounts do **not** automatically become platform/brand admins. Plan explicit migrations before production.
- The existing development configuration uses `hibernate.ddl-auto: update`; **do not apply this patch directly to production data** without backup, a reviewed SQL migration, verified grants and rollback plan. Verify MySQL 8.4 with a disposable test database first.
- Some other services use numeric tenant IDs, but contracts must still be unified before live integration.

## Security notes / current limitations

- Token version changes invalidate all existing tokens per user. A JWT with an old version is rejected after an account update; each request reloads the current account.
- JWT verification is scoped to Identity Service; **other microservices and Gateway are not protected by this patch**. Configure authenticated trusted identity propagation and direct-service access restrictions before deploying to an untrusted network.
- User-role administration never accepts a public caller-supplied role. Cross-tenant and role-management restrictions are checked in the server-side service.
- High-risk permissions currently use a platform administrator explicit grant. For production, design and enforce approval records, limits, and per-action confirmation if business policy requires them.
- This package preserves older API responses for compatibility; `/api/auth/me` exposes expanded roles/grants/scope. Frontend must use appropriate account fields.
- Audit events only cover Identity operations. All services must emit events with consistent actor/tenant/action/object/correlation IDs to meet the full audit/traceability requirement.
- The brand logo URL and kiosk content must be rendered safely by the kiosk frontend (escape/sanitize HTML), not inserted as untrusted HTML.

## Suggested verification before marking P1-04 ready

1. Identity `mvn clean test`: old `AuthServiceTest` and adapted `SecurityAuthorizationTest` pass, plus new tests.
2. MySQL 8.4 dedicated test schema starts with new entities and keeps legacy user records.
3. Create one platform admin, two tenants, one brand admin per tenant, and accounts for Operations Manager, Technician, Inventory Staff and Viewer.
4. Verify account cannot read another tenant's user data; brand admin cannot assign `PLATFORM_SUPER_ADMIN` or sensitive grants.
5. Verify disabled/locked account and role change reject tokens issued before the change.
6. Verify tenant suspension blocks brand-side writes; document unresolved Order/Machine webhook/refund decisions.
7. Check Gateway's new identity route paths and original Catalog/Order/Machine/Reporting paths remain valid.
8. Confirm all cross-service enforcement changes as separate reviews before claiming overall RBAC DONE.

**Evidence:** This package was authored from the reviewed GitHub source and statically inspected. Maven was unavailable in the assistant's execution environment, so passing Maven tests or production DB validation is **not claimed**. Use the included installer `-RunTests` on Windows and retain Maven logs.
