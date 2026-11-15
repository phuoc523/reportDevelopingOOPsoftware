# ScentStation Identity Service

Identity Service runs on port `8081` by default. It supports the existing customer
authentication API and a separate staff authentication flow backed by `staff_users`
and `staff_user_roles`.

## Clone the project

Clone the team's ScentStation repository and check out the account-lifecycle branch:

```powershell
git clone <ScentStation-repository-url>
cd <repository-folder>
git checkout feature/OOP-11-p102-account-lifecycle
cd services/identity-service
```

Replace the placeholders with the repository URL and local folder name provided by
the team.

## Requirements

- JDK 17 or newer
- Maven 3.9 or newer
- MySQL 8.0.45 (configure the MySQL service to listen on `127.0.0.1:3307`, or set
  `DB_URL` to the port used on your machine)

## Database setup

Create the schema and apply the non-destructive bootstrap SQL on a **new, empty**
development database:

```powershell
mysql -h 127.0.0.1 -P 3307 -u root -p < src/main/resources/db/setup/identity-schema.sql
```

The script uses `CREATE DATABASE/TABLE IF NOT EXISTS`; it does not drop, truncate,
or alter existing tables. On a database that already contains these tables, inspect
the schema and data before running any database script. The application uses
`spring.jpa.hibernate.ddl-auto=none` and never creates or updates tables at startup.
There is no Flyway/Liquibase migration history in this service yet; the SQL file is
a bootstrap for new local databases, not an upgrade migration.

Create a dedicated local application user once, using a password chosen on that
machine (do not copy a real password into this README):

```sql
CREATE USER 'scentstation_app'@'127.0.0.1' IDENTIFIED BY '<local-db-password>';
GRANT SELECT, INSERT, UPDATE, DELETE
    ON scentstation_identity.* TO 'scentstation_app'@'127.0.0.1';
```

Configure connection settings in the PowerShell process that will run Maven:

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3307/scentstation_identity'
$env:DB_USERNAME = 'scentstation_app'
$env:DB_PASSWORD = '<your-local-mysql-password>'

# Generate a fresh 256-bit HMAC key for this local environment.
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Do not commit passwords, `.env` files, JWT keys, or the demo password. `JWT_SECRET`
is required and must be a Base64-encoded key of at least 32 bytes.

## Opt-in demo accounts

Demo seeding is disabled by default and is rejected when the active Spring profile
is `prod` or `production`. Set the shared demo password in the process environment
and explicitly enable the seed before starting a local service:

```powershell
$env:SCENTSTATION_DEMO_PASSWORD = '<choose-a-local-password-of-8-to-72-characters>'
$env:DEMO_ACCOUNTS_ENABLED = 'true'
```

Start Identity Service from this directory:

```powershell
mvn spring-boot:run
```

At startup the seed creates tenant `DEMO` with a generated UUID and `active=true`
only if that tenant code does not exist. An existing tenant is used as-is. It then
creates only missing usernames and their initial role rows; existing passwords,
roles, and account status are never reset. The platform account has a null tenant.
The six logins are:

| Username | Role | Tenant |
|---|---|---|
| `platform.admin` | `PLATFORM_SUPER_ADMIN` | none |
| `demo.admin` | `BRAND_ADMIN` | `DEMO` |
| `demo.a` | `OPERATIONS_MANAGER` | `DEMO` |
| `demo.b` | `TECHNICIAN` | `DEMO` |
| `demo.c` | `INVENTORY_STAFF` | `DEMO` |
| `demo.d` | `VIEWER` | `DEMO` |

Password hashes are generated with BCrypt. Disable the seed after first startup:

```powershell
$env:DEMO_ACCOUNTS_ENABLED = 'false'
```

## Authentication APIs

Customer authentication remains unchanged:

```http
POST /api/auth/login
Content-Type: application/json

{"username":"customer-name","password":"..."}
```

Staff authentication is separate:

```http
POST /api/auth/staff/login
Content-Type: application/json

{"username":"demo.admin","password":"..."}
```

The staff response includes a Bearer JWT, staff ID, role list, and tenant ID. Staff
JWTs carry `principal_type=STAFF`; customer JWTs carry `principal_type=CUSTOMER`.
The filter reloads staff account status and roles from the database, and checks
that a tenant is still active, so changed roles and disabled/locked accounts take
effect for existing tokens. Passwords and password hashes are never JWT claims.

Try a seeded login without putting the password in the source tree:

```powershell
$body = @{
    username = 'demo.admin'
    password = $env:SCENTSTATION_DEMO_PASSWORD
} | ConvertTo-Json
Invoke-RestMethod -Uri 'http://localhost:8081/api/auth/staff/login' `
    -Method Post -ContentType 'application/json' -Body $body
```

### Staff account lifecycle

All lifecycle routes require a staff JWT:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/admin/staff-users` | List accounts |
| `GET` | `/api/admin/staff-users/{id}` | View an account |
| `POST` | `/api/admin/staff-users` | Create an account |
| `PUT` | `/api/admin/staff-users/{id}` | Update username, tenant, and roles |
| `PATCH` | `/api/admin/staff-users/{id}/enabled` | Enable/disable |
| `PATCH` | `/api/admin/staff-users/{id}/lock` | Manually lock/unlock |
| `PUT` | `/api/admin/staff-users/{id}/roles` | Replace roles |
| `PUT` | `/api/admin/staff-users/{id}/password` | Reset password |

`PLATFORM_SUPER_ADMIN` can manage all tenants and roles. `BRAND_ADMIN` can manage
accounts in its own tenant and cannot grant either administrative role. Other roles
cannot use account lifecycle APIs. Cross-tenant account lookups return not found.
Platform super-admin accounts must have only the platform role and no tenant;
all other staff accounts require an active tenant.

The configured lockout defaults to five consecutive wrong passwords and 15 minutes.
Override them with `LOGIN_MAX_FAILED_ATTEMPTS` and `LOGIN_LOCK_DURATION` (for example
`20m`). A manual lock does not expire automatically. Disabled, locked, unknown-user,
and invalid-password login failures use the same public error message.

### RBAC and tenant scope

- `/api/tenants/**` and `/api/admin/users/**`: platform super-admin only.
- `/api/admin/staff-users/**`: platform super-admin or tenant-scoped brand admin.
- `/api/staff/operations/**`: platform/brand admin/operations manager may write;
  viewer may read.
- `/api/staff/maintenance/**`: platform/brand admin/operations manager/technician
  may write; viewer may read.
- `/api/staff/inventory/**`: platform/brand admin/operations manager/inventory staff
  may write; viewer may read.
- `/api/staff/reports/**`: platform super-admin, brand admin, operations manager,
  and viewer may read; writes are restricted to platform super-admin.
- Deletes in the operations, maintenance, and inventory route groups are restricted
  to platform super-admin.

The Identity Service applies tenant scope to staff account lifecycle operations and
places the current tenant in the staff authentication principal/JWT. Each downstream
service must also constrain its own data queries by that tenant; a signed tenant
claim alone is not a substitute for resource-level authorization.

The confirmed `tenants` table stores only `id`, `code`, and `active`. Accordingly,
tenant management requests use `tenantCode` and tenant responses expose those
persisted fields; the earlier source model's name/brand/configuration fields do not
exist in the live schema.

## Tests

```powershell
mvn test
```

Tests use mocks and do not connect to or modify the development MySQL database.

## Troubleshooting

- **Cannot connect / access denied:** confirm MySQL is listening on port `3307`,
  the database and app user exist, and `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` are
  set in the same terminal as Maven.
- **JWT secret missing or invalid:** set `JWT_SECRET` to Base64 for at least 32
  random bytes; do not use a committed key.
- **Demo seed does not run:** set `DEMO_ACCOUNTS_ENABLED=true` and restart. A missing
  or invalid `SCENTSTATION_DEMO_PASSWORD` makes startup fail rather than creating
  accounts with a fallback password.
- **Demo login rejected:** check that the staff account is enabled, not manually
  locked, its tenant is active, and that the role rows are correct. An existing
  account is intentionally not reset by the seed.
- **401 vs 403:** `401` means missing/invalid authentication or unavailable
  credentials; `403` means authenticated but insufficient role/scope.
- **Schema mismatch:** startup never runs DDL. Compare the database with
  `src/main/resources/db/setup/identity-schema.sql` and resolve schema differences
  explicitly; do not enable Hibernate `update` on a database with live data.
