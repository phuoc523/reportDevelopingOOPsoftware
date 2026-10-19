# Order Service

Order Service creates orders with product snapshots, calculates order totals,
returns order details, and manages order status.

Orders are currently held in memory. Data is lost when the service restarts.
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
