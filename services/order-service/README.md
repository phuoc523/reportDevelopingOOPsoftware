# Order Service

Order Service creates orders with product snapshots, calculates order totals,
returns order details, manages order status, and provides a simulated QR
payment flow.

Orders and payments are currently held in memory. Data is lost when the service restarts.
No database or catalog-service connection is required; product name and price
are captured from the create-order request.

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
- `PATCH /api/orders/{id}/status` — update order status.
- `POST /api/orders/{id}/payments/qr` — create or retrieve the order's pending
  mock payment and QR payload. Repeated requests return the same payment.
- `GET /api/payments/{reference}` — look up payment status and details.
- `POST /api/payments/{reference}/simulate` — simulate successful payment.

When calling these routes directly in a browser, replace `{id}` with the
numeric order ID (for example, `http://localhost:8083/api/orders/1` to view the
full order as JSON). Shell variables such as `$($order.id)` are expanded only
inside PowerShell commands, not in a browser address bar.

The QR response contains a `qrPayload` URI (`scentstation://pay/...`) that a
client can render as a QR image. This is a mock payload, not a connection to a
payment provider. A successful simulation marks the payment `PAID` and moves a
`PENDING` order to `CONFIRMED`. Simulating the same successful payment again is
safe and returns the existing paid payment. A paid order cannot be cancelled;
the API returns `409 Conflict`. An unpaid payment cannot be processed after its
order is cancelled, and a new QR payment cannot be created for that order.

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
- `CONFIRMED` → `PROCESSING` or `CANCELLED`
- `PROCESSING` → `SHIPPED` or `CANCELLED`
- `SHIPPED` → `DELIVERED`

Invalid order data returns `400`, unknown order IDs return `404`, and invalid
status transitions return `409`.

## Test

```bash
mvn test
```
