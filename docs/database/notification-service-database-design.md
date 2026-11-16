# Notification Service Database Design

## Status

Implemented by Flyway migration `V1__create_notification_schema.sql` in Notification Service. H2/MySQL-mode migration and Hibernate schema validation passed in tests. A live MySQL server and deployed database have not been verified.

## Design Decisions

- Notification content and recipient-specific state are separate records so one notification can have many recipients with independent read timestamps.
- `tenant_id` and `recipient_id` are opaque identifiers owned by other services. Notification Service stores the values needed for filtering but creates no foreign keys into Identity, Machine, or other service databases.
- The Machine Service event's `tenantId` is a string while Identity currently models tenant IDs numerically. No cross-service mapping contract was verified; string storage avoids coupling this schema to either database representation. Mapping/validation remains an integration responsibility.
- `source_event_id` is optional and unique together with `source` to provide a database-level duplicate guard for a future event integration. Phase 2 does not implement a receiver or complete event-processing/idempotency flow.
- `notification_type` and `priority` are bounded strings rather than enums because an approved closed set/mapping has not been defined. Future APIs/integrations must validate accepted values against a confirmed contract.
- `read_at` is per recipient. There is no notification-wide read flag. Delivery/channel status is not modeled because delivery belongs to Phase 5.

## Tables

### `notifications`

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | `BIGINT` | Auto-increment primary key |
| `tenant_id` | `VARCHAR(100)` | Required external tenant identifier; not an FK |
| `title` | `VARCHAR(200)` | Required display title |
| `content` | `VARCHAR(4000)` | Required in-app message content |
| `notification_type` | `VARCHAR(50)` | Required type label; allowed values remain contract-defined |
| `priority` | `VARCHAR(20)` | Required priority/severity label; mapping remains contract-defined |
| `source` | `VARCHAR(50)` | Required originating service/source label |
| `source_event_id` | `VARCHAR(100)` | Optional source event identifier |
| `created_at` | `DATETIME(6)` | Required creation time |
| `updated_at` | `DATETIME(6)` | Required last-update time |

Constraints and indexes:

- `pk_notifications` — primary key on `id`.
- `uk_notifications_tenant_id` — unique `(tenant_id, id)` to support tenant-consistent child references.
- `uk_notifications_source_event` — unique `(source, source_event_id)`; multiple null event IDs are allowed by MySQL.
- `idx_notifications_tenant_created` — `(tenant_id, created_at, id)` for tenant-scoped chronological queries.

### `notification_recipients`

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | `BIGINT` | Auto-increment primary key |
| `tenant_id` | `VARCHAR(100)` | Required tenant discriminator matching the parent notification |
| `notification_id` | `BIGINT` | Required local FK to `notifications` |
| `recipient_id` | `VARCHAR(100)` | Required opaque recipient identifier; not an Identity DB FK |
| `read_at` | `DATETIME(6)` | Null means unread; timestamp means read |
| `created_at` | `DATETIME(6)` | Required association creation time |
| `updated_at` | `DATETIME(6)` | Required association update time |

Constraints and indexes:

- `pk_notification_recipients` — primary key on `id`.
- `uk_notification_recipient` — unique `(notification_id, recipient_id)` prevents duplicate association for one recipient.
- `fk_notification_recipient_tenant` — composite local FK `(tenant_id, notification_id)` to `notifications(tenant_id, id)`, preventing a recipient row from claiming a different tenant than its notification.
- `idx_notification_recipient_inbox` — `(tenant_id, recipient_id, read_at, created_at, id)` supports tenant/recipient inbox and unread-count queries.

## Relationships

- `Notification` 1 → many `NotificationRecipient`.
- `NotificationRecipient` belongs to exactly one `Notification`.
- All foreign keys are within `scentstation_notification`.
- No foreign keys reference Identity, Machine, or any other service's database.

## Migration and Runtime Schema Policy

- Flyway manages schema changes under `services/notification-service/src/main/resources/db/migration/`.
- `V1__create_notification_schema.sql` creates both tables and indexes on a new database.
- Hibernate remains `ddl-auto: validate`; it checks mappings but does not create/update tables.
- Tests use H2 in MySQL compatibility mode to run Flyway and validate the JPA mappings. Production MySQL compatibility still requires an environment-backed verification.
