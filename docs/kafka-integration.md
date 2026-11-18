# Kafka integration

Kafka is used for asynchronous state propagation where the producing service must
not lose an event when another service is unavailable.

## Implemented flows

| Producer | Topic | Consumer | Idempotency key | Purpose |
|---|---|---|---|---|
| Machine Service | `machine-alert.v1` | Notification Service | alert identity | Create operational notifications |
| Order Service | `payment-events.v1` | Reporting Service | payment reference + status | Project payment state into reports |
| Order Service | `dispense-events.v1` | Operation Service | dispensing `commandId` | Deduct installed-bottle inventory after a confirmed spray |

Order writes payment/dispense state and its outbox row in the same local database
transaction. A scheduled publisher sends the event and deletes the row only after
Kafka acknowledges it. A broker outage therefore delays propagation without
rolling back a completed payment or losing a confirmed dispense result.

Delivery is **at least once**, not exactly once. A process crash after Kafka accepts
an event but before the outbox row is deleted can produce a duplicate. Reporting
upserts by payment reference and Operation returns the existing consumption for a
previously processed `commandId`, so both consumers are idempotent.

Invalid payloads and events that still fail after retries are written to the
matching `.DLT` topic. Do not delete DLT or outbox records to hide failures; fix the
data/dependency and replay deliberately.

## Observability

Order Service exports `scentstation_order_outbox_pending{event="payment|dispense"}`.
Prometheus raises `OrderKafkaOutboxBacklog` when a queue remains non-empty for ten
minutes. Kafka client metrics are also exposed through the service Actuator
Prometheus endpoints.

## Remaining synchronous boundaries

Commands that require an immediate authoritative answer stay synchronous: Identity
authorization, catalog price validation, machine/slot availability, and submitting
a physical dispense command. Replacing these with events would require a saga and
an explicit pending-state UX; Kafka alone cannot make a cross-service operation
ACID. Reporting dashboard snapshot reads also remain HTTP because they are queries,
not durable state-change notifications.
