# ScentStation — P1-03 Staff Authentication & Sessions

Independent Spring Boot 3.5.6 / Java 21 microservice prototype for Task P1-03. Uses MySQL 8, Spring Security, BCrypt and **opaque bearer tokens** (not JWT). Only a SHA-256 hash of the 256-bit random token is stored in MySQL. Tokens expire and are revoked server-side. No customer kiosk account is required by this API.

## Files and ownership

`src/main/java/com/scentstation/identity/`: `auth/` (authentication and sessions), `web/` (HTTP contract), `security/` (bearer filter), `model/` and `repository/` (JPA), `config/`, `bootstrap/` (opt-in demo fixtures). Tenant/user/roles are **minimal integration entities**, not replacement P1-01/P1-02/P1-04 functionality.

## Run in VS Code (Windows PowerShell)

Prerequisites: JDK 21, Maven 3.9+, MySQL 8; install VS Code Extension Pack for Java and Spring Boot Extension Pack.

```powershell
cd services/identity-service
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS scentstation_identity;"
$env:DB_URL = "jdbc:mysql://localhost:3306/scentstation_identity"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "YOUR_LOCAL_PASSWORD"
mvn spring-boot:run
```

Service URL: `http://localhost:8081`. In development JPA uses `ddl-auto: update` to create tables. Use Flyway/Liquibase migrations before production; do not use DDL auto-update in production. Never commit secrets. MySQL user should have minimum needed permissions.

To create one example staff user **only in local demo mode**, set `$env:DEMO_ADMIN_PASSWORD` to a strong 12+ character password and run `mvn spring-boot:run '-Dspring-boot.run.profiles=demo'`. Username `demo.admin`, role `BRAND_ADMIN`, tenant `DEMO`. Default startup creates **no accounts**. P1-02 will own actual account creation and password resets.

## API contracts

| Method | Endpoint | Auth | Result |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Public staff login | 200 `{accessToken,tokenType,expiresAt,user:{userId,username,tenantId,roles}}`; 401 invalid; 423 locked; 400 invalid input |
| GET | `/api/v1/auth/me` | `Authorization: Bearer <token>` | 200 `{userId,username,tenantId,roles}` or 401 |
| POST | `/api/v1/auth/logout` | `Authorization: Bearer <token>` | 204 and token immediately revoked; otherwise 401 |

Example request:
```json
{"username":"demo.admin","password":"YOUR_DEMO_PASSWORD"}
```

```powershell
$body = @{ username='demo.admin'; password=$env:DEMO_ADMIN_PASSWORD } | ConvertTo-Json
$login = Invoke-RestMethod http://localhost:8081/api/v1/auth/login -Method Post -ContentType 'application/json' -Body $body
$headers = @{ Authorization="Bearer $($login.accessToken)" }
Invoke-RestMethod http://localhost:8081/api/v1/auth/me -Headers $headers
Invoke-RestMethod http://localhost:8081/api/v1/auth/logout -Method Post -Headers $headers
```

Default session TTL = 30 minutes; lock after 5 failed attempts for 15 minutes. Configurable via `AUTH_SESSION_TTL_MINUTES`, `AUTH_MAX_FAILED_ATTEMPTS`, `AUTH_LOCK_MINUTES`. Username normalized to lowercase. Login attempts are persisted with outcome/time; successful or failed login timestamps are persisted on staff record when the record exists. Avoid logging request bodies, passwords, or bearer tokens.

## Tests

```powershell
mvn test
```

`AuthIntegrationTest`: login, role/tenant identity, logout revocation, invalid credentials with audit, lockout, expired token, disabled account, suspended tenant and anonymous denial. Uses H2 MySQL-mode in a test profile (not a live MySQL integration test).

## Integration contract and security notes

- P1-01/02/04 must reconcile the stub Tenant/StaffUser/role schema against the group's canonical DB schema before merging.
- The opaque-token scheme requires **Identity Service verification** (or a shared trusted validation contract) at the API Gateway and downstream services. A service must never trust a client-supplied `tenantId` or role.
- Route customer kiosk traffic via explicitly public, machine-scoped endpoints designed by P3/P4/P5; do not expose staff credentials or grant staff roles to anonymous customers.
- Bearer tokens are secrets: use HTTPS in deployed environments and never store them in publicly accessible web storage. Rate-limit login by IP at Gateway in addition to per-user lockout.
- Session revocation after account disable and permission changes is supported by `SessionService.revokeAllForUser`; wire this into P1-02 and P1-04 once completed.
- This demo does not implement refresh tokens, password recovery, session management UI or an OAuth identity provider. These are outside P1-03 as currently assigned.

## Git workflow (do not commit on main)

```powershell
git clone https://github.com/phuoc523/reportDevelopingOOPsoftware.git
cd reportDevelopingOOPsoftware
git switch -c feature/p1-03-auth-session
# Copy the `services/identity-service/` folder from this deliverable into the cloned repository.
git add services/identity-service
git commit -m "feat(identity): implement P1-03 staff authentication and session management"
git push -u origin feature/p1-03-auth-session
```

Create a PR from `feature/p1-03-auth-session` into `main`, request a code review, and resolve schema/contracts with teammates before merging.
