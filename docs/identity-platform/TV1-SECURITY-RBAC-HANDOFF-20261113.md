# ScentStation – TV1 Identity & Platform RBAC hardening (2026-11-13)

**Source:** `test-2026-11-07-latest` (local working branch `fix/tv1-complete-rbac`).
**Scope:** Identity Service, public API Gateway trust boundary, TV1 authorization contract, tests. Does NOT edit TV2–TV5 business code.

## Counts and implemented decisions

- **9 role enums:** `PLATFORM_SUPER_ADMIN`, `BRAND_ADMIN`, `OPERATIONS_MANAGER`, `TECHNICIAN`, `INVENTORY_STAFF`, `VIEWER`, `CUSTOMER`; retained legacy `ADMIN`, `STAFF` (no default fine-grained grants and no implicit legacy URL access after this patch).
- **37 permission enums:** `TENANT_MANAGE`, `PLATFORM_CONFIG_MANAGE`, `PLATFORM_ROLE_MANAGE`, `PLATFORM_AUDIT_READ`, `PLATFORM_REPORT_READ`, `DEVICE_ACCESS_REVOKE`, `BRAND_PROFILE_READ`, `BRAND_PROFILE_EDIT`, `BRAND_USER_MANAGE`, `BRAND_ROLE_MANAGE`, `CATALOG_READ`, `CATALOG_MANAGE`, `CAMPAIGN_MANAGE`, `LOCATION_READ`, `LOCATION_MANAGE`, `MACHINE_READ`, `MACHINE_CONTROL`, `MACHINE_CONFIGURE`, `ORDER_READ`, `ORDER_REVIEW`, `REFUND_REQUEST`, `REFUND_APPROVE`, `INVENTORY_READ`, `INVENTORY_MANAGE`, `INVENTORY_ADJUST`, `MAINTENANCE_READ`, `MAINTENANCE_MANAGE`, `DIAGNOSTIC_SPRAY`, `ALERT_READ`, `ALERT_MANAGE`, `REPORT_READ`, `REPORT_EXPORT`, `AUDIT_READ`, `NOTIFICATION_READ`, `NOTIFICATION_CONFIGURE`, `KIOSK_BROWSE`, `KIOSK_ORDER`.
- Default matrix sizes: Platform Super Admin **37**, Brand Admin **25** (not 27), Operations Manager **14**, Technician **6**, Inventory Staff **7**, Viewer **10**, Customer **2**, ADMIN **0**, STAFF **0**.
- High-risk grants are `REFUND_APPROVE`, `INVENTORY_ADJUST`, `DIAGNOSTIC_SPRAY`, `MACHINE_CONFIGURE`, `DEVICE_ACCESS_REVOKE`. Outside Platform Super Admin, grant explicitly through Platform Admin; audit grant changes.

## Changes in this patch

1. `AccessPolicy.scope`: a Technician with no assigned machine or an unspecified machine ID is now rejected. Callers for list/search endpoints **must filter by assigned machines**; they may not use a null machineId to infer unrestricted tenant access.
2. `SecurityConfig`: explicit routes for Identity authentication, audit and private introspection; deny-by-default for unknown paths. Historical `ADMIN`/`STAFF` role names no longer bypass route-level guards. This intentionally changes legacy URL behavior; migrate old accounts before rollout.
3. `JwtAuthenticationFilter`: validates that a user's assigned tenant still exists and is ACTIVE on every request. Previously-issued JWTs for suspended tenants cannot authenticate. Public Customer accounts with no tenant remain supported.
4. `JwtService`: a JWT without `tokenVersion` is rejected instead of silently equating to version 0. Database tokenVersion remains authoritative.
5. `POST /internal/auth/introspect`: private Identity contract accepting **both** the end-user `Authorization: Bearer ...` and `X-Internal-Service-Key`; returns fresh `AccountPrincipal` (id, username, tenantId, roles, grants, locationIds, machineIds), only if JWT/account/tenant are valid. Required `INTERNAL_SERVICE_KEY` environment variable; absent key **fails closed**. Private HTTPS/network only; do not expose publicly.
6. API Gateway global filter strips incoming untrusted role/tenant/identity and service-key headers, rejects any path containing the `internal` segment, and issues a fresh `X-Request-Id`. It preserves the end-user Bearer token so downstream services can introspect it. Gateway **does not authorize downstream business operations**.
7. Tests added for role/permission counts, high-risk rights, Technician default-deny scope, legacy route restrictions, suspended tenant, private introspection key, Gateway spoofed headers and internal route blocking.

## Boundary and assignment for TV2–TV5

**TV2 – Catalog/Inventory:** `CATALOG_READ`, `CATALOG_MANAGE`, `CAMPAIGN_MANAGE`, `INVENTORY_READ`, `INVENTORY_MANAGE`, `INVENTORY_ADJUST`, `LOCATION_READ`, `LOCATION_MANAGE`. Enforce tenant ID and object ownership at DB query boundaries; reject invalid batch/bottle/slot cross-tenant relationships, audit inventory adjustment with reason.

**TV3 – Order/Payment:** `KIOSK_ORDER` (customer creation), `ORDER_READ`, `ORDER_REVIEW`, `REFUND_REQUEST`, `REFUND_APPROVE`. Check tenant, machine/location scope and token for staff APIs; verify provider-signed callbacks separately (NOT staff JWT); implement idempotent refund/reconciliation/dispensing boundaries and audit.

**TV4 – Machine/IoT:** `MACHINE_READ`, `MACHINE_CONTROL`, `MACHINE_CONFIGURE`, `MAINTENANCE_READ`, `MAINTENANCE_MANAGE`, `DIAGNOSTIC_SPRAY`, `ALERT_READ`, `ALERT_MANAGE`, `DEVICE_ACCESS_REVOKE`. Validate real resource tenant/location/machine from database **before** authorization; only assigned Technician machines; diagnostic spray separate from sales, secure device command and maintenance safety. Device credentials are separate from staff JWT.

**TV5 – Frontend/Reporting:** `REPORT_READ`, `REPORT_EXPORT`, `PLATFORM_REPORT_READ`, `NOTIFICATION_READ`, `NOTIFICATION_CONFIGURE`, `AUDIT_READ`, `PLATFORM_AUDIT_READ`, `KIOSK_BROWSE`. UI must display capabilities appropriately, but cannot be the sole authorization layer. Backend reporting queries must be tenant-scoped and platform aggregates restricted.

### How downstream calls Identity introspection (private network only)

```
POST http://identity-service:8081/internal/auth/introspect
Authorization: Bearer <end-user-jwt>
X-Internal-Service-Key: <shared-secret-from-environment>
```

- Set `INTERNAL_SERVICE_KEY` to an independently generated, long random secret; configure trusted clients separately. Do **not** send it through browsers/kiosks/public Gateway.
- If unavailable, unauthorized, revoked, wrong tenant, suspended tenant, missing explicit permission, or wrong assigned machine/location => **fail closed**; do not trust `X-Tenant-Id`, `X-Verified-*`, `X-Role`, etc.
- Query target resource using its actual stored tenantId/locationId/machineId and verify all appropriate scope dimensions. Do not rely solely on caller-supplied IDs. For list endpoints, constrain DB queries by allowed assignments, and require a service owner to define machine/location tenancy validation on assignment.
- Apply `RolePermissions.allowed(...)` semantics downstream from a **versioned and agreed contract**; avoid duplicating logic inconsistently. The Identity response is validated per request; cache, if any, must have a revocation-aware policy.
- Do not route or expose `/internal/**` on the Gateway. In staging/deployment, block direct access to private Identity ports except from trusted services, and enforce TLS/mTLS or a private trusted network with TLS.

## Verification evidence and limitations

- User's pre-patch run: Identity **26/26 tests PASS**, Java 21, Maven 3.10.0; this is baseline evidence only.
- Assistant validated with local `javac/java` the **unchanged role and permission matrix**: nine roles, 37 permissions, Brand Admin 25, legacy/default and sensitive grants.
- This patch has **not yet passed Maven tests** in the assistant environment because Maven dependencies/command are not available offline. New JUnit/Spring tests are included for user execution.
- Need `mvn clean test` in both Identity and Gateway after applying patch. Next, live MySQL tenant A vs B, old JWT after suspension/revocation, internal service-key rejection, real role/permission API checks, and E2E service tests. No statement that overall project or all P1 Jira items are DONE yet.
- This does not provide service-specific endpoint authorization in TV2–TV5; their owners must implement it. It also does not validate whether machine/location IDs assigned to a user belong to their tenant: TV4's machine/location ownership APIs or a shared service contract are required.
- Production prerequisites: managed secret and service key, database migrations instead of `ddl-auto:update`, logging/monitoring, network segmentation, rate limiting, alert/audit policy, load-testing and security review.

## Windows PowerShell retest

```powershell
$env:JAVA_HOME = 'E:\XD\JDK21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$mvn = 'E:\XD\NV1\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd'
cd 'E:\Dự án\1 Đồ án XDPM\ScentStation'
& $mvn -f services/identity-service/pom.xml -B -ntp clean test
if ($LASTEXITCODE -ne 0) { throw 'Identity tests failed' }
& $mvn -f services/api-gateway/pom.xml -B -ntp clean test
if ($LASTEXITCODE -ne 0) { throw 'Gateway tests failed' }
git diff --check
git diff --stat
```

**Do not merge to `test-2026-11-07-latest` until both builds and tenant/role negative tests succeed.**
