# Order Service

Order Service creates orders with product snapshots, calculates order totals,
returns order details, manages order status, and provides a simulated QR
payment flow.

Orders and payments are currently held in memory. Data is lost when the service restarts.
No database or catalog-service connection is required; product name and price
are captured from the create-order request.
Dispensing commands and manual-review markers are also held in memory for this
simulation and are lost on restart.

## Run

Requires JDK 25 or newer.

```bash
mvn spring-boot:run
```

The default port is `8083`; override it with `ORDER_SERVICE_PORT`.

## API

- `POST /api/orders` — create an order.
- `GET /api/orders` — list all orders.
- `GET /api/orders/{id}` — get order details.
- `GET /api/orders/{id}/status` — look up order status.
- `GET /api/orders/{id}/dispensing` — query the current Machine Service result
  for the order's existing dispensing command. This never creates a new command.
- `PATCH /api/orders/{id}/status` — update order status.
- `POST /api/orders/{id}/payments/qr` — create or retrieve the order's pending
  mock payment and QR payload. Repeated requests return the same payment.
- `GET /api/payments/{reference}` — look up payment status and details.
- `POST /api/payments/callback` — verify a mock-provider callback and process it
  idempotently.
- `POST /api/payments/{reference}/simulate` — simulate successful payment.

When calling these routes directly in a browser, replace `{id}` with the
numeric order ID (for example, `http://localhost:8083/api/orders/1` to view the
full order as JSON). Shell variables such as `$($order.id)` are expanded only
inside PowerShell commands, not in a browser address bar.

The QR response contains a `qrPayload` URI (`scentstation://pay/...`) that a
client can render as a QR image. This is a mock payload, not a connection to a
payment provider. Payments use VND and expire 15 minutes after creation by
default (`PAYMENT_QR_EXPIRATION`, ISO-8601 duration, for example `PT15M`).
Expiration is enforced when a payment is read or processed; an expired pending
payment also moves its order to `EXPIRED`. Expired payments cannot be paid or
replaced with a new QR.

Payment confirmation is server-controlled. The order status API cannot set
`CONFIRMED`; only a verified payment callback or the local mock simulation can
do that. The callback requires the `X-Payment-Callback-Token` header, compared
against `PAYMENT_CALLBACK_TOKEN`. For local development only, the fallback is
`local-mock-callback-token`; set a private value in every shared deployment.
This is a mock authentication token, not a payment-provider signature. Do not
expose the mock callback or `/simulate` as a production payment integration.

Callback request:

```http
POST /api/payments/callback
X-Payment-Callback-Token: <configured token>
Content-Type: application/json
```

```json
{
  "eventId": "provider-event-001",
  "reference": "PAY-...",
  "orderId": 1,
  "amount": 25.5,
  "currency": "VND",
  "status": "PAID"
}
```

Allowed callback statuses are `PAID` and `FAILED`. The backend verifies the
reference/order association, exact amount, currency, payment state, and expiry
before changing payment state. A successful callback marks the payment `PAID`
and moves a pending order to `CONFIRMED`; `FAILED` marks both payment and order
as `FAILED`, preventing a misleading pending order or another QR attempt.
Unknown/mismatched values return a client error, and cancelled, failed, or
expired payments cannot be confirmed. The same `eventId` and payload can be
retried safely; reusing an event ID with a different payload returns `409`.
Error responses retain the numeric HTTP code in `status` (for example `404`)
and use an uppercase HTTP code name in `error` (for example `NOT_FOUND`).
Callback event outcomes and payment data are held in memory for this stage and
are lost when the service restarts.

The `/simulate` route is a development convenience: it submits an internally
generated mock callback through the same verification and idempotency flow.
A paid order cannot be cancelled; the API returns `409 Conflict`. An unpaid
payment cannot be processed after its order is cancelled.

Demo sequence:

1. Create an order with `POST /api/orders`.
2. Request its QR payment with `POST /api/orders/{id}/payments/qr`.
3. Simulate the buyer paying with `POST /api/payments/{reference}/simulate`.
4. Check payment with `GET /api/payments/{reference}` and order status with
   `GET /api/orders/{id}/status`.

The `Payment` model is intentionally backed by an in-memory repository for this
project stage. No MySQL schema, datasource, or database dependency is enabled.

Create-order request example:

```json
{
  "customerId": 7,
  "machineId": "MACHINE-001",
  "slotId": "SLOT-01",
  "items": [
    {
      "productId": 3,
      "productName": "Scent Sample",
      "unitPrice": 12.75,
      "quantity": 2
    }
  ]
}
```

New orders start in `PENDING`. Allowed transitions are:

- `PENDING` → `CONFIRMED` or `CANCELLED`
- `CONFIRMED` → `DISPENSED`, `PROCESSING` or `CANCELLED`
- `DISPENSED` → `PROCESSING`
- `PROCESSING` → `SHIPPED` or `CANCELLED`
- `SHIPPED` → `DELIVERED`
- `PENDING` → `EXPIRED` when its QR payment expires
- `PENDING` → `FAILED` when the payment provider reports failure

Invalid order data returns `400`, unknown order IDs return `404`, and invalid
status transitions return `409`.

## Dispensing orchestration

After a payment callback has been verified and the order is marked `CONFIRMED`,
Order Service creates one in-memory `commandId` and submits
`POST /api/machines/dispense-commands` to Machine Service. `tenantId` comes from
`MACHINE_SERVICE_TENANT_ID` (default `TENANT-001`); the client cannot set it.
Set `MACHINE_SERVICE_BASE_URL` to the Machine Service base URL (default
`http://localhost:8084`). Machine/slot IDs are supplied in the create-order
request and captured on the order.

For this assignment's local demo, `MACHINE_SERVICE_SIMULATION_ENABLED` defaults
to `true`. In this mode Order Service does not call another service: its client
simulates an `ACCEPTED` response followed by the configured final result
(`MACHINE_SERVICE_SIMULATED_RESULT`, default `DISPENSED`) on the dispensing
lookup. This lets the full payment-to-dispensed flow run with only
`order-service`. The mock command results are in memory and reset on restart.
To use the real Machine Service later, set
`MACHINE_SERVICE_SIMULATION_ENABLED=false` and configure
`MACHINE_SERVICE_BASE_URL`.

`ACCEPTED` and `PROCESSING` are not proof of dispensing. Order status changes
to `DISPENSED` only after a matching `orderId` and `commandId` result explicitly
reports `DISPENSED`. Use `GET /api/orders/{id}/dispensing` to poll
`GET /api/machines/dispense-commands/{commandId}`. A request or lookup timeout,
empty response, ID mismatch, or unrecognized result is recorded as `UNKNOWN`
with `manualReviewRequired: true`; the same command ID is retained and no
second dispense request is automatically created. A confirmed `FAILED` machine
result moves the order to `FAILED` and is not retried. See
[`contracts/openapi/order-machine-dispensing.yaml`](../../contracts/openapi/order-machine-dispensing.yaml)
for the shared API contract. The tagged coordination document says this
endpoint proposal is pending confirmation by both service owners.

## Test

```bash
mvn test
```
