# Order Service

Order Service creates orders with product snapshots, calculates order totals,
returns order details, manages order status, and provides a simulated QR
payment flow.

Orders and payments are currently held in memory. Data is lost when the service restarts.
No database or catalog-service connection is required; product name and price
are captured from the create-order request.
Dispensing commands and manual-review markers are also held in memory for this
simulation and are lost on restart.
Transaction review cases and reconciliation results are in-memory as well; this
service intentionally does not configure a database.

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
- `GET /api/payments/reviews` and `GET /api/payments/reviews/{reference}` — list
  or inspect transaction reviews, including the reason and manual-review status.
- `POST /api/payments/reviews/{reference}/resolve` — manually resolve a review.
- `POST /api/payments/reconciliation` — compare service payments with simulated
  provider records and flag missing, unexpected, or mismatched records.
- `GET /swagger-ui.html` — interactive Swagger UI; `GET /v3/api-docs` returns
  the generated OpenAPI document.

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

### Swagger UI: which section to open

Open `http://localhost:8083/swagger-ui.html`. Each endpoint appears under one
of the three group headings visible in Swagger UI. For every operation, expand
its row, click **Try it out**, fill in the path/header/body values, then click
**Execute**. Do not paste a URL template containing `{id}` directly into the
browser; enter the actual ID in that operation's parameter field.

| Swagger UI section | Operation to select | Purpose |
| --- | --- | --- |
| **Orders** | `POST /api/orders` | Create an order. Use the prefilled **Valid order** example; record response `id`. |
| **Payments** | `POST /api/orders/{orderId}/payments/qr` | Create/retrieve QR payment. Enter the Order `id`; record `reference`, `orderId`, and `amount`. |
| **Payments** | `POST /api/payments/{reference}/simulate` | Demo-only successful payment. Enter the payment `reference`. |
| **Payments** | `POST /api/payments/callback` | Process a provider-style callback. Use a newly created, not-yet-paid payment. |
| **Payments** | `GET /api/payments/{reference}` | Check payment status. |
| **Orders** | `GET /api/orders/{id}/status` | Check order status. |
| **Orders** | `GET /api/orders/{id}/dispensing` | Check the dispensing result after payment. |
| **transaction-review-controller** | `POST /api/payments/reconciliation` | Compare simulated provider records with service payments. |
| **transaction-review-controller** | `GET /api/payments/reviews` | List transaction review cases. |
| **transaction-review-controller** | `GET /api/payments/reviews/{reference}` | Read the review reason and manual-review status. |
| **transaction-review-controller** | `POST /api/payments/reviews/{reference}/resolve` | Mark a review as manually resolved. |

The section name `transaction-review-controller` is the tag currently shown in
the Swagger screenshot; the routes still use `/api/payments/...`.

#### Demo A — create an order and simulate payment

1. Under **Orders**, open `POST /api/orders`. Click **Try it out** and keep
   the provided **Valid order** JSON unchanged:

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

   Click **Execute**. Expected: HTTP `201`, response `status` is `PENDING`,
   `totalAmount` is `25.50`. Record `id` (for example, `1`).
2. Under **Payments**, open `POST /api/orders/{orderId}/payments/qr`. Click
   **Try it out**, enter the recorded order ID in `orderId`, and click
   **Execute**. Expected: HTTP `200`, payment `status` is `PENDING`. Record
   `reference`, `orderId`, and `amount`.
3. Under **Payments**, open `POST /api/payments/{reference}/simulate`. Enter
   the recorded payment reference and click **Execute**. Expected: HTTP `200`,
   payment `status` is `PAID`.
4. Under **Payments**, call `GET /api/payments/{reference}` with that
   reference. Expected: `status: PAID`.
5. Under **Orders**, call `GET /api/orders/{id}/status` with the order ID.
   Expected after payment: `CONFIRMED`. Then call
   `GET /api/orders/{id}/dispensing`; with default simulation, expected result
   is `DISPENSED`. Call the order status endpoint again; it should now show
   `DISPENSED`.

#### Demo B — callback through Swagger (instead of `/simulate`)

Use a second, newly created order and QR payment. Do **not** call
`/simulate` for this payment.

1. Create the order under **Orders** (`POST /api/orders`) and create its QR
   under **Payments** (`POST /api/orders/{orderId}/payments/qr`), as in Demo A.
2. Under **Payments**, open `POST /api/payments/callback`, then click
   **Try it out**.
3. In the `X-Payment-Callback-Token` parameter field, enter
   `local-mock-callback-token` for the default local setup. If
   `PAYMENT_CALLBACK_TOKEN` was configured to another value, enter that value.
4. In the request body, replace `reference`, `orderId`, and `amount` with the
   values from the QR response. Use a new/unique `eventId`, `currency: "VND"`,
   and `status: "PAID"`. Example:

   ```json
   {
     "eventId": "swagger-callback-001",
     "reference": "PAY-REPLACE-WITH-QR-REFERENCE",
     "orderId": 1,
     "amount": 25.50,
     "currency": "VND",
     "status": "PAID"
   }
   ```

5. Click **Execute**. Expected: HTTP `200`, payment `status: PAID`.
6. Verify under **Payments** with `GET /api/payments/{reference}` and under
   **Orders** with `GET /api/orders/{id}/status`. Expected statuses are `PAID`
   and `CONFIRMED`; call the dispensing endpoint to get the simulated final
   result.

#### Demo C — reconciliation and manual review

1. Start with an order and payment created as in Demo A. Record the payment
   reference and use the payment's **current** status in the reconciliation
   request (`PENDING` before payment, or `PAID` after callback/simulation).
2. Under **transaction-review-controller**, open
   `POST /api/payments/reconciliation`, click **Try it out**, and submit:

   ```json
   {
     "payments": [
       {
         "reference": "PAY-REPLACE-WITH-QR-REFERENCE",
         "orderId": 1,
         "amount": 25.50,
         "currency": "VND",
         "status": "PAID"
       }
     ]
   }
   ```

   Replace the example reference/order ID and set the actual current payment
   status. Expected when it matches: `checkedTransactions: 1`,
   `matchedTransactions: 1`, `discrepancyCount: 0`.
3. To test a missing provider record, submit `{"payments":[]}` to the same
   reconciliation endpoint. Expected: a discrepancy with status `OPEN` and
   `manualReviewRequired: true`.
4. Still under **transaction-review-controller**, call
   `GET /api/payments/reviews` to list cases, then
   `GET /api/payments/reviews/{reference}` to see the reason. To close the
   case after manual investigation, use
   `POST /api/payments/reviews/{reference}/resolve`; expected status becomes
   `RESOLVED` and `manualReviewRequired` becomes `false`.

To test HTTP `400` for order validation, use the **Orders** section and open
`POST /api/orders` (the same operation used to create a valid order). Click
**Try it out**, replace the request body with the invalid example below, then
click **Execute**. The response should be HTTP `400`; the exact validation
message identifies the field that failed:

```json
{
  "customerId": 7,
  "machineId": "MACHINE-001",
  "slotId": "SLOT-01",
  "items": []
}
```

All validation/business errors use the JSON shape `status`, `error`, `message`,
`path`, and `timestamp`. HTTP `400 BAD_REQUEST` indicates malformed JSON,
invalid fields, or mismatched callback details; `401 UNAUTHORIZED` indicates a
missing/invalid callback token; `404 NOT_FOUND` means the order/payment is
unknown; `409 CONFLICT` means the requested payment or status transition is
incompatible with the current state. The create-order operation documents its
field requirements and a valid request example in OpenAPI.

Payment confirmation is server-controlled. The order status API cannot set
`CONFIRMED`; only a verified payment callback or the local mock simulation can
do that. The callback requires the `X-Payment-Callback-Token` header, compared
against `PAYMENT_CALLBACK_TOKEN`. For local development only, the fallback is
`local-mock-callback-token`; set a private value in every shared deployment.
This is a mock authentication token, not a payment-provider signature. Do not
expose the mock callback or `/simulate` as a production payment integration.

The callback request is under the **Payments** section at
`POST /api/payments/callback`.

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

### Transaction review and reconciliation

Paid transactions whose dispensing result is `FAILED` or `UNKNOWN` open a
manual-review case. Review responses include the payment reference, order,
reason, status (`OPEN` or `RESOLVED`), and whether manual review remains
required. Reconciliation accepts simulated provider records with `reference`,
`orderId`, `amount`, `currency`, and `status` (`PENDING`, `PAID`, `FAILED`, or
`EXPIRED`). Missing, unexpected, or inconsistent records open review cases.
Later reconciliation runs do not automatically resolve existing cases; resolve
them explicitly after investigation.

Example reconciliation request:

```json
{
  "payments": [
    {
      "reference": "PAY-...",
      "orderId": 1,
      "amount": 25.5,
      "currency": "VND",
      "status": "PAID"
    }
  ]
}
```

The `/simulate` route is a development convenience: it submits an internally
generated mock callback through the same verification and idempotency flow.
A paid order cannot be cancelled; the API returns `409 Conflict`. An unpaid
payment cannot be processed after its order is cancelled.

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

## Docker

From this directory, build and run the service without a database:

```bash
docker build -t scentstation/order-service .
docker run --rm -p 8083:8083 scentstation/order-service
```

Configure the payment and Machine Service environment variables documented
above as needed. The container exposes port `8083`; an API Gateway can route
`/api/orders/**` and `/api/payments/**` to this service. Gateway routing and the
repository-level Compose file are outside this service's scope.
