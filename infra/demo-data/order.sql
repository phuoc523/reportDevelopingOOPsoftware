SET NAMES utf8mb4;

INSERT INTO orders
    (id, tenant_id, customer_id, machine_id, slot_id, total_amount, currency,
     status, expires_at, created_at, updated_at)
VALUES
    (91701, '1', 93001, '9101', '91001', 45000.0000, 'VND', 'DELIVERED',
     CURRENT_TIMESTAMP(6) + INTERVAL 1 DAY, CURRENT_TIMESTAMP(6) - INTERVAL 12 DAY, CURRENT_TIMESTAMP(6) - INTERVAL 12 DAY),
    (91702, '9102', 93002, '9102', '91002', 65000.0000, 'VND', 'DISPENSED',
     CURRENT_TIMESTAMP(6) + INTERVAL 1 DAY, CURRENT_TIMESTAMP(6) - INTERVAL 8 DAY, CURRENT_TIMESTAMP(6) - INTERVAL 8 DAY),
    (91703, '9102', 93003, '9102', '91003', 70000.0000, 'VND', 'CONFIRMED',
     CURRENT_TIMESTAMP(6) + INTERVAL 1 DAY, CURRENT_TIMESTAMP(6) - INTERVAL 3 DAY, CURRENT_TIMESTAMP(6) - INTERVAL 3 DAY),
    (91704, '9103', 93004, '9103', '91004', 68000.0000, 'VND', 'PENDING',
     CURRENT_TIMESTAMP(6) + INTERVAL 1 DAY, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91705, '9103', 93005, '9103', '91005', 72000.0000, 'VND', 'FAILED',
     CURRENT_TIMESTAMP(6) + INTERVAL 1 DAY, CURRENT_TIMESTAMP(6) - INTERVAL 1 DAY, CURRENT_TIMESTAMP(6) - INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id),
    customer_id = VALUES(customer_id),
    machine_id = VALUES(machine_id),
    slot_id = VALUES(slot_id),
    total_amount = VALUES(total_amount),
    currency = VALUES(currency),
    status = VALUES(status),
    expires_at = VALUES(expires_at),
    updated_at = VALUES(updated_at);

INSERT INTO order_items
    (id, order_id, product_id, product_name, unit_price, quantity, line_total)
VALUES
    (91801, 91701, 91001, 'ScentStation Signature No. 01', 45000.0000, 1, 45000.0000),
    (91802, 91702, 91002, 'Bleu de Chanel Eau de Parfum', 65000.0000, 1, 65000.0000),
    (91803, 91703, 91003, 'Coco Mademoiselle Eau de Parfum', 70000.0000, 1, 70000.0000),
    (91804, 91704, 91004, 'Dior Sauvage Eau de Parfum', 68000.0000, 1, 68000.0000),
    (91805, 91705, 91005, 'J''adore Eau de Parfum', 72000.0000, 1, 72000.0000)
ON DUPLICATE KEY UPDATE
    order_id = VALUES(order_id),
    product_id = VALUES(product_id),
    product_name = VALUES(product_name),
    unit_price = VALUES(unit_price),
    quantity = VALUES(quantity),
    line_total = VALUES(line_total);

INSERT INTO payments
    (reference, order_id, amount, currency, qr_payload, status, created_at, expires_at, paid_at)
VALUES
    ('DEMO-PAY-91701', 91701, 45000.0000, 'VND', 'SCENTSTATION|DEMO-PAY-91701|45000', 'PAID',
     CURRENT_TIMESTAMP(6) - INTERVAL 12 DAY, CURRENT_TIMESTAMP(6) - INTERVAL 12 DAY + INTERVAL 15 MINUTE, CURRENT_TIMESTAMP(6) - INTERVAL 12 DAY + INTERVAL 2 MINUTE),
    ('DEMO-PAY-91702', 91702, 65000.0000, 'VND', 'SCENTSTATION|DEMO-PAY-91702|65000', 'PAID',
     CURRENT_TIMESTAMP(6) - INTERVAL 8 DAY, CURRENT_TIMESTAMP(6) - INTERVAL 8 DAY + INTERVAL 15 MINUTE, CURRENT_TIMESTAMP(6) - INTERVAL 8 DAY + INTERVAL 1 MINUTE),
    ('DEMO-PAY-91703', 91703, 70000.0000, 'VND', 'SCENTSTATION|DEMO-PAY-91703|70000', 'PAID',
     CURRENT_TIMESTAMP(6) - INTERVAL 3 DAY, CURRENT_TIMESTAMP(6) - INTERVAL 3 DAY + INTERVAL 15 MINUTE, CURRENT_TIMESTAMP(6) - INTERVAL 3 DAY + INTERVAL 3 MINUTE),
    ('DEMO-PAY-91704', 91704, 68000.0000, 'VND', 'SCENTSTATION|DEMO-PAY-91704|68000', 'PENDING',
     CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6) + INTERVAL 1 DAY, NULL),
    ('DEMO-PAY-91705', 91705, 72000.0000, 'VND', 'SCENTSTATION|DEMO-PAY-91705|72000', 'FAILED',
     CURRENT_TIMESTAMP(6) - INTERVAL 1 DAY, CURRENT_TIMESTAMP(6) - INTERVAL 1 DAY + INTERVAL 15 MINUTE, NULL)
ON DUPLICATE KEY UPDATE
    order_id = VALUES(order_id),
    amount = VALUES(amount),
    currency = VALUES(currency),
    qr_payload = VALUES(qr_payload),
    status = VALUES(status),
    created_at = VALUES(created_at),
    expires_at = VALUES(expires_at),
    paid_at = VALUES(paid_at);

INSERT INTO transaction_reviews
    (reference, order_id, reason, status, created_at, resolved_at)
VALUES
    ('DEMO-PAY-91705', 91705, 'Thanh toán thất bại cần đối soát với nhà cung cấp.', 'OPEN', CURRENT_TIMESTAMP(6) - INTERVAL 1 DAY, NULL)
ON DUPLICATE KEY UPDATE
    order_id = VALUES(order_id),
    reason = VALUES(reason),
    status = VALUES(status),
    created_at = VALUES(created_at),
    resolved_at = VALUES(resolved_at);
