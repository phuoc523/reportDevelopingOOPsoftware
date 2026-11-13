# Reporting Service

## Backend verification and manual API testing

See [backend validation report](BACKEND-VALIDATION.md) for tested scope, build
results, real-service tests and remaining integration limits. Use the
[Postman collection](postman/ReportingBackend.postman_collection.json) with the
[manual test guide](postman/README.md) to test without Admin Web.

Dashboard responses now include server-derived `access.scope`, `access.tenantId`,
`access.canReadFinancialReports` and `access.canExport`. These describe the current
verified user's access; API authorization is still enforced on every request.

## Local integration

Run `docker compose up -d --build` from the repository root and run
`pwsh -File scripts/Test-ReportingIntegration.ps1` after the backend starts.
The smoke test writes a demo order and checks that Reporting receives its payment
and updates the same transaction from PENDING to PAID. It also checks that
the dashboard can reach Machine and Operation through their internal HTTP APIs.
It verifies that replaying a payment keeps the same Reporting row and that
internal synchronization rejects requests without an API key.
For a custom key, set `REPORTING_SYNC_API_KEY` before starting Compose and running
the script, or pass `-SyncApiKey` to the script. Use `-GatewayUrl` and
`-ReportingUrl` when running on different local ports.

- Dashboard via Gateway: `GET http://localhost:8080/api/reporting/dashboard?tenantId=1&period=day`.
- Reporting stores its own copy of payments by payment reference. Order pushes
  updates after committing the payment transaction. If the push fails, Order
  logs the failure and the payment remains committed.
- Replay a payment using
  `POST http://localhost:8086/api/reporting/internal/transactions/sync-from-order/{reference}`
  with the `X-Internal-Api-Key` header. Set the same `REPORTING_SYNC_API_KEY`
  for Order and Reporting. The default key in Compose is for local development.
- The dashboard summarizes all Reporting transactions. Its tenant parameter
  applies to machines, alerts and inventory. Operation expects a positive numeric
  tenant ID. Check the dashboard's `upstream` values when a source is unavailable.

## Order synchronization guarantees (Phase 2)

- Both push and replay validate required fields, status/timestamps, and the
  `DECIMAL(15,2)` amount before persistence. Invalid Order responses return 502;
  invalid push payloads return 400.
- Replaying identical PENDING or terminal data does not write another row.
  An existing reference must keep its order, amount, currency and creation time.
  PAID cannot be replaced by PENDING or another terminal status.
- Existing rows are locked during synchronization to serialize concurrent updates.
  Concurrent first inserts are protected by the unique reference. A duplicate insert,
  deadlock or lock acquisition failure returns 409; replay the request after the
  competing transaction finishes. This does not provide automatic delivery retries.
- An unavailable Order response does not write or clear Reporting transactions.
  Durable retry/outbox delivery from Order remains work for the Order owner.

Run the standard tests with:

```powershell
mvn -f .\services\reporting-service\pom.xml clean test
```

`OrderPaymentClientHttpTest` uses a controlled HTTP server, including a real read
timeout. `TransactionSyncMySqlTest` is opt-in and requires a **separate test database**:
set `REPORTING_MYSQL_TEST_URL`, `REPORTING_MYSQL_TEST_USERNAME`, and
`REPORTING_MYSQL_TEST_PASSWORD`, then run `mvn test -Dtest=TransactionSyncMySqlTest`
from this service directory. Set `ORDER_SERVICE_URL` to also exercise a real Order
service. That test creates a demo order/payment and leaves its test records intact;
it never resets or clears database tables.

## Additional Reporting APIs

### Identity and Catalog integration (Phase 5)

Public Reporting business APIs now require `Authorization: Bearer <token>`.
Reporting forwards the token only to the configured Identity `/api/auth/me` and
uses the verified server profile. Identity checks JWT signature/expiry, active/locked
account state and tokenVersion. Reporting does not decode unverified claims, accept
client role/tenant headers, share Identity's JWT secret, or cache authentication.

Set `IDENTITY_SERVICE_URL` to the actual Identity URL. The local default is
`http://localhost:8081`; inside Docker use `http://identity-service:8081`.
The root Compose does not currently pass this variable to Reporting; coordinate
that config change before running the rebuilt Reporting container. No Gateway or
Compose change was made in this phase.

- GET health stays public. Internal synchronization still requires its existing
  `X-Internal-Api-Key`; it does not depend on Identity or Bearer authentication.
- Reporting projects only REPORT_READ, REPORT_EXPORT and PLATFORM_REPORT_READ
  from Identity's current RolePermissions source. VIEWER precedence and explicit
  grants follow that source. A non-platform grant cannot manufacture platform access.
- The dashboard requires REPORT_READ. Non-platform users use their Identity tenant;
  an explicit different tenant or multiple tenant parameters returns 403. Accounts
  with location/machine allowlists cannot read whole-tenant aggregates until those
  aggregates support assigned scopes; such requests return 403.
- Non-platform dashboard responses have `transactions=null` and
  `upstream.order-service=unsupported-tenant-transaction-schema`; the global
  transaction repository is not queried. Their Machine/Operation data remains
  scoped to the verified tenant.
- Existing transaction/revenue/refund APIs require PLATFORM_REPORT_READ because
  the Reporting transaction schema has no tenant ownership. Exports require both
  REPORT_EXPORT and PLATFORM_REPORT_READ. A tenant REPORT_READ/REPORT_EXPORT grant
  alone cannot authorize cross-tenant financial reports. Global manual transaction
  creation is limited to the platform permission; other public writes are denied.
- Missing/invalid/revoked tokens return 401, denied permissions/scopes return 403,
  Identity timeout/outage returns 503, and malformed Identity responses return 502.
  Requests fail closed and tokens/keys are not logged by this integration.

Identity currently returns roles and explicit grants, not effective permissions.
**Coordination with Identity owner:** expose authoritative effective permissions
or a permission-decision API with tenant/scope information to replace the local
reporting-only projection and avoid policy drift. Existing role projection tests
are based on the checked-in Identity source and must be updated when that policy changes.

**Catalog findings:** GET `/api/products` and `/api/products/{id}` provide product
name, tenantId, description, fragranceNotes, imageUrl, defaultPrice and availability.
ProductResponse does not expose a brand field. ProductService requires trusted
tenant context from `X-Verified-*`; Catalog currently does not validate the forwarded
Bearer token itself, and the Gateway source has no filter establishing that trusted
context. Reporting therefore does not fabricate these headers or enable product
enrichment against this boundary.

**Coordination with Catalog/Gateway owners:** provide an authenticated tenant-scoped
service-to-service product lookup (preferably batch lookup by product IDs), or an
implemented Gateway authentication boundary with headers stripped/overwritten and
direct backend access controlled. Once agreed, Reporting can map inventory productId
to product name; brand enrichment requires a separate explicit brand contract.
No Catalog/Identity/Gateway source or database schema was modified.

`IdentityClientHttpTest` uses a controlled HTTP server; `ReportingSecurityTest`
uses a mocked Identity transport with the production filter/security rules.
`IdentityReportingIntegrationTest` is opt-in via `IDENTITY_SERVICE_URL`: it registers
a real CUSTOMER through Identity, validates its JWT, verifies forged role headers
cannot elevate access, logs out and verifies immediate token revocation. It uses
the production Reporting security chain with request probes, not a fake JWT or a
test-only authentication bypass. The sample customer account is retained. Real
platform/tenant authorization still needs provisioned accounts with those roles;
the default integration test does not promote users or edit Identity tables.

Older anonymous smoke scripts/clients now need a real authorized Bearer token for
business reads. Updating external scripts, Gateway, Compose and Admin Web requires
coordination outside this service; their existing files were left unchanged.

### Machine reporting (Phase 3)

Use the existing endpoint: `GET /api/reporting/dashboard?tenantId=12&period=day`.
The response retains `machines`, `alerts`, `transactions`, `inventory`, and
`upstream`, and adds `machineLocations`:

```json
{
  "machineLocations": [{
    "locationId": "LOBBY-A",
    "name": "Lobby",
    "active": true,
    "metadataAvailable": true,
    "machines": {
      "total": 3, "online": 1, "offline": 1, "unstable": 0,
      "disabled": 0, "maintenance": 1, "error": 0, "unknown": 0
    }
  }]
}
```

- Counts use `operationalStatus`; ONLINE is not a claim that dispensing is
  available. Slot state, stock and operating hours belong to the availability API.
- Locations with no machines are included. Missing location metadata leaves
  `name`/`active` null and `metadataAvailable=false`; an unassigned machine uses
  `locationId=null`. No machines are discarded when location metadata is missing.
- `upstream.machine-locations` describes the location request independently of
  `upstream.machine-service` and `upstream.machine-alerts`. If machines are
  unavailable, `machineLocations` is empty; clients must check `upstream` before
  interpreting zero counts as confirmed data.
- Both machine and location lists require tenant context in the actual Machine
  service. Without a tenant, Reporting skips those requests and reports
  `skipped-missing-tenant`. This is not a cross-tenant machine report.
- Null/invalid bodies and mismatched tenant data from the machine/location lists
  are marked unavailable. An empty JSON array is a confirmed empty result.
- Machine status counts include an additive `unknown` field for future statuses.
- Reporting makes one request each for machines, locations and alerts. It does
  not fetch availability/history per machine or persist snapshots. The existing
  detail, availability and history APIs remain available from Machine directly.
  These figures describe current state, not historical machine uptime.

The client uses `MACHINE_SERVICE_URL` (local default in `application.yml` is
`http://localhost:8084`; Docker uses `http://machine-service:8084`) with a 3-second
connect timeout and a 5-second read timeout per request.

`MachineReportingIntegrationTest` is opt-in via `MACHINE_SERVICE_URL`. It creates
isolated sample location/machine records through the real Machine API and keeps
them for inspection. Machine HTTP calls are real; unrelated transaction and
Operation dependencies are mocked. Run it from this service directory with
`mvn test -Dtest=MachineReportingIntegrationTest`.

### Operation reporting (Phase 4)

The same dashboard adds `operations.batches`, `operations.consumption`, and
`operations.maintenance`. Use a canonical positive numeric tenant ID shared by
Machine and Operation. Missing/invalid tenants skip Operation calls.

- Existing `inventory` keeps its bottle status counts. `operations.batches`
  reports batch count, expired batch count and `receivedBottles` (received quantity
  across batches, **not** current remaining stock).
- Consumption uses only `CUSTOMER_CONSUMPTION` and `DIAGNOSTIC_CONSUMPTION`
  movements. Source inspection confirms Operation creates these movements only
  for SUCCESS after deducting stock. INSTALL/REFILL/ADJUSTMENT/DISPOSE movements
  are excluded. Quantity is `quantityBefore - quantityAfter`, retaining the
  source's quantity unit; Reporting does not invent a unit or convert it.
- `successfulCustomerCommands` and `successfulDiagnosticCommands` count distinct
  confirmed command references separately. They are not paid order counts,
  created dispensing command counts, or a claim about the number of spray pulses.
  Identical duplicate command movements count once; conflicting duplicates mark
  consumption unavailable.
- `failedCommands` is null and `upstream.operation-failures` is
  `unsupported-missing-list-api`. The source only exposes lookup by command ID,
  so Reporting cannot enumerate failed/TIMEOUT/UNCERTAIN commands reliably.
- The dashboard period filters consumption by movement `createdAt`, with inclusive
  start/end dates. Batches, bottle states and maintenance are **current state**,
  not historical snapshots filtered to that period.
- Maintenance includes `byStatus`, active, overdue and active critical ticket
  counts. COMPLETED/CANCELLED tickets are excluded from active/overdue counts.
  Unknown future statuses are counted separately and not assumed active.
- New sections are null on source failure, with separate `operation-batches`,
  `operation-consumption` and `operation-maintenance` upstream flags. The existing
  `operation-service` flag refers to the bottle request. Failures in one source
  do not suppress successful sections from other sources.

Operation uses LocalDateTime values without an offset. `OPERATION_SERVICE_ZONE_ID`
defaults to UTC to match the current Docker JVM; set it to the source JVM's actual
zone when running elsewhere. `REPORTING_TIME_ZONE` defaults to
`Asia/Ho_Chi_Minh` for Operation aggregation; this setting does not change the JVM
timezone or existing payment report timestamps. Reporting converts movement dates into the reporting zone and
compares maintenance due times as instants. Tests cover the UTC/Vietnam day boundary.

The client reads `/api/inventory/bottles`, `/api/inventory/batches`,
`/api/inventory/movements`, and `/api/maintenance/tickets` once each. It validates
tenant ownership, required fields and consumption quantities. Requests have a
3-second connect timeout and a 5-second read timeout. No shared database access,
new tables, or per-bottle estimation/history requests were added.

`OperationReportingIntegrationTest` is opt-in via `OPERATION_SERVICE_URL`. It
creates a sample batch, bottle, installation, customer success, diagnostic success,
failed command and maintenance ticket through the real Operation API. It checks
remaining quantity, command lookup, estimation, maintenance history and Reporting
aggregation. Sample records are kept. Unrelated Machine and transaction dependencies
are mocked; Operation's current `docker-local` machine-slot adapter is also a local
stub. This verifies the Operation ledger/API, not physical dispensing hardware.

**Coordination needed with Operation owner:** add a paginated consumption listing
with tenant, time range, dispense type and result filters, or an equivalent summary
API, to report failures and scale period reporting without loading all movements.
The current movement endpoint returns all tenant history; Reporting filters it in
memory. The estimation API mixes customer/diagnostic successes and requires bottle
calibration, so it is not used as a replacement for the consumption report.

All date filters use ISO format `YYYY-MM-DD`. Date ranges are inclusive at both ends.

### Transaction search
`GET /api/reporting/transactions`

Optional query parameters: `reference`, `orderId`, `status`, `startDate`, `endDate`,
`minAmount`, `maxAmount`, `page` (default 0), `size` (default 20, maximum 200),
`sortBy` (id, createdAt, paidAt, amount, reference, status), `direction` (asc/desc).

Examples:
- `/api/reporting/transactions?status=PAID&page=0&size=20`
- `/api/reporting/transactions?orderId=ORDER-001&startDate=2026-10-01&endDate=2026-10-31`
- `GET /api/reporting/transactions/1`
- `GET /api/reporting/transactions/reference/TXN-20261030-001`

### Detailed revenue report
`GET /api/reporting/revenue/details?startDate=2026-10-01&endDate=2026-10-31`

Returns daily successful transaction counts and revenue. Revenue includes only PAID transactions and is grouped by `paid_at`.

### Transaction status report
`GET /api/reporting/transactions/status-summary?startDate=2026-10-01&endDate=2026-10-31`

Returns counts and percentages for PAID, PENDING, FAILED, EXPIRED, and REFUNDED.
The reporting period is based on `created_at`.

### Basic refund report
- `GET /api/reporting/refunds/summary?startDate=2026-10-01&endDate=2026-10-31`
- `GET /api/reporting/refunds?startDate=2026-10-01&endDate=2026-10-31&page=0&size=20`

**Current basic refund convention:** the existing database schema has no separate refund amount/date columns.
A transaction with status `REFUNDED` is counted as refunded, and its `amount` is treated as the recorded refund amount.
This does not model partial refunds or multiple refunds per transaction. The incoming transaction API accepts `REFUNDED` as a status so this basic reporting path can be tested; actual payment-provider integration remains separate.

### Excel export
- `GET /api/reporting/export/transactions` supports the same filters as transaction search (without pagination).
- `GET /api/reporting/export/revenue?startDate=2026-10-01&endDate=2026-10-31`

Excel exports use `.xlsx` and require the Apache POI dependency added to `pom.xml`.

### Database compatibility
Reporting initializes an empty MySQL database with `schema.sql`.
Hibernate validates the table at startup.
