# Identity and RBAC handoff - ScentStation

This is the **identity-side authorization contract**; it does not mean downstream services are secure yet.

## Ownership

Phung (Identity & Gateway) owns tenant registration and lifecycle, platform/brand user accounts, roles, permissions, JWT verification **within Identity**, token-version revocation, administrative audit, and the Gateway's Identity routes.

Members owning Catalog, Order/Payment, Machine/IoT, Inventory and Reporting own authorization **in their service**, including object-level tenant checks and associated database queries. Never rely on hiding UI actions or client-supplied identity headers.

## Roles and permission names

`PLATFORM_SUPER_ADMIN`, `BRAND_ADMIN`, `OPERATIONS_MANAGER`, `TECHNICIAN`, `INVENTORY_STAFF`, `VIEWER`, `CUSTOMER`; `ADMIN` and `STAFF` are legacy names and do not confer platform privileges.

Permission names are defined in `services/identity-service/src/main/java/com/scentstation/identity/rbac/Permission.java`. The default role matrix is `RolePermissions.java`. High-risk permissions (`REFUND_APPROVE`, `INVENTORY_ADJUST`, `DIAGNOSTIC_SPRAY`, `MACHINE_CONFIGURE`) must be explicitly granted by Platform Super Admin. An account bearing VIEWER remains read-only even if a grant or another role was stored.

## Administrative API via Identity (:8081) / Gateway (:8080)

| Route | Method | Caller | Function |
|---|---|---|---|
| `/api/platform/tenants` | GET/POST | Platform Super Admin | List/create tenant |
| `/api/platform/tenants/{id}` | GET/PATCH | Platform Super Admin | Detail/profile |
| `/api/platform/tenants/{id}/status` | PATCH | Platform Super Admin | Suspend/reactivate |
| `/api/brand/tenant` | GET/PATCH | Brand Admin | Own tenant profile |
| `/api/platform/users` | GET/POST | Platform Super Admin | Create/list accounts |
| `/api/brand/users` | GET/POST | Brand Admin | Create/list own tenant accounts |
| `/api/{platform|brand}/users/{id}` | GET/PATCH | Authorized admin | Detail or update `email`, `fullName` only |
| `/api/{platform|brand}/users/{id}/roles` | PATCH | Authorized admin | Assign roles |
| `/api/{platform|brand}/users/{id}/grants` | PATCH | Authorized admin | Replace explicit grants |
| `/api/{platform|brand}/users/{id}/scopes` | PATCH | Authorized admin | Assign location/machine IDs |
| `/api/{platform|brand}/users/{id}/status` | PATCH | Authorized admin | Enable/lock |
| `/api/{platform|brand}/users/{id}/reset-password` | POST | Authorized admin | Password reset |
| `/api/auth/login`, `/api/auth/logout`, `/api/auth/me` | POST/POST/GET | Login/logout/authenticated | Session/user context |
| `/api/audit/recent` | GET | Authorized admin | Identity audit trail |

Profile PATCH body: `{"email":"user@example.com","fullName":"Updated Person"}`. It cannot change tenant, roles, permissions, scopes or password.

## Required downstream checks (members 2-5)

For every protected operation: **validate token with a trusted verifier or Identity introspection and ensure it has not been revoked; determine current account/tenant; check permission; verify resource tenant ID and assigned location/machine scope; audit sensitive changes**.

Do **not** trust `X-Tenant-Id`, `X-Role`, `X-User-Id` or any analogous client-controlled header. Gateway merely routes Identity requests in this patch; it does **not** verify the JWT for each downstream service. A signed identity propagation/introspection contract and enforcement need to be implemented by each service owner. The `GET /api/auth/me` endpoint is for an authenticated user's own context; it is not a standalone service-to-service introspection contract.

For scope ID assignments, Identity validates positive IDs only. Machine/Location owner must provide a tenant-scoped existence/ownership validation API or shared contract before treating location/machine scope assignments as fully validated. For tenant suspension, Identity records state; cross-service treatment of pending payments/webhooks/dispense outcomes requires a team decision.

## Verification and limitations

After applying this handoff hardening, run `mvn clean test` independently in `services/identity-service` and `services/api-gateway`, and test with two distinct tenants in MySQL before any production deployment. A passing build is **not** proof of cross-service tenant isolation. Hibernate schema update is for local development; schema migration requires separate production review.

Gateway `org.springframework.cloud.gateway` and `org.springframework.web.cors` logging levels should not be DEBUG/TRACE in shared or production environments. Never commit `.env`, token, passwords, `*.bak` or local build outputs.