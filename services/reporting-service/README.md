# Reporting Service

## Additional Reporting APIs

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
No table columns or database schema changes are required. Hibernate remains configured with `ddl-auto: validate`.
