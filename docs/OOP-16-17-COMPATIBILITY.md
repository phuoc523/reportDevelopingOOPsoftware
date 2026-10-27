# OOP-16/OOP-17 — Registration, JWT and RBAC compatibility

**Status: source candidate, NOT verified on the user's local GitHub branch. Tests and team review required.**

## Existing behavior retained
- Existing `POST /api/v1/auth/login`, `/logout`, `/me` still work.
- Default `auth.jwt.enabled=false` preserves the opaque token contract and earlier tests.
- Gateway `/api/v1/identity/**` remains unchanged; optional OOP-12 aliases are additive.
- Existing staff users retain their roles. No public flow may choose staff/admin privileges.

## Public registration
`POST /api/v1/auth/register` with JSON `{"username":"demo.customer","email":"person@example.com","password":"my-strong-password"}`.
- Expected HTTP `201` with `{userId,username,email}`; never returns password hashes.
- Login then uses `POST /api/v1/auth/login` with `{username,password}`.
- Input required: username 3–120 ASCII `[a-z0-9._-]`, email format, password 8–72 characters **and at most 72 UTF-8 bytes**.
- Username and email uniqueness checked in service *and enforced in the database*, conflict HTTP 409.
- Password BCrypt(12), existing Spring `PasswordEncoder` bean.
- Regardless of caller-supplied `roles`, public registration always creates CUSTOMER with no staff permissions and no tenant. This uses existing `StaffUser` storage for compatibility; future domain split requires a separately approved migration.
- `email` column is nullable for legacy staff; new registration requires it. Production requires reviewed SQL migration/index after checking existing duplicates. Do not rely on `ddl-auto=update` outside development.
- Rate limiting, CAPTCHA / email verification and abuse monitoring are recommended before exposure on the public internet. This patch is a classroom/demo implementation, not production-ready account onboarding.

## Optional JWT mode
`auth.jwt.enabled` is **false by default**. Before enabling, generate an independent 32+ random byte secret and store its standard Base64 representation in `JWT_SECRET_BASE64` (do not print or commit it). Example PowerShell:

```powershell
$key = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($key)
$secret = [Convert]::ToBase64String($key)
# Store in an approved local secret store / deployment secret manager; do not paste into chat or Git.
```

Set `JWT_ENABLED=true` and `JWT_SECRET_BASE64` in the launch environment. After startup, a new login produces a signed HS256 JWT with issuer, subject, iat, exp and jti. As with opaque tokens, `auth_sessions` remains authoritative for revocation, expiry, disabled users and per-request session status; JWTs are **not stateless permissions**. Existing opaque sessions stay usable when JWT is enabled. Turning JWT off will stop validating JWT sessions, so coordinate rollbacks.

Identity introspection still verifies the token against both cryptographic claims (JWT mode) and server session. Other services must **not** trust JWT claims without verifying it or using the existing authenticated Identity introspection contract.

## Optional OOP-12 alias routes
- `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/logout` forward via Gateway to `/api/v1/auth/...`.
- `/api/admin/rbac/**` maps to `/api/v1/rbac/**` (still subject to Identity authentication and RBAC).
- `/api/admin/**` is intentionally **not** a wildcard route to arbitrary internal Identity endpoints. Expand only after agreeing the actual admin API contract.
- Gateway recognizes both `IDENTITY_URL` and fallback `IDENTITY_SERVICE_URL`; `IDENTITY_URL` takes priority when both are set.

## Tests and review before Jira Done
- `AuthIntegrationTest`, `RbacIntegrationTest`, `TenantScopeIntegrationTest`, `PlatformModulesIntegrationTest`, `RegistrationIntegrationTest`, `JwtAuthIntegrationTest`.
- `GatewayContractTest` includes new alias rewrite and no exposure of arbitrary admin/internal endpoints.
- Test with MySQL and verify email unique index; test duplicate username/email, disabled account and JWT logout revocation.
- Agree with OOP-19 and other service owners whether token type and public user registration are actually in-scope. Coordinate frontend and downstream JWT support before changing default mode.
- No secret, token or password in commits/logs/evidence.
