CREATE TABLE IF NOT EXISTS orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    customer_id BIGINT NOT NULL,
    machine_id VARCHAR(100) NOT NULL,
    slot_id VARCHAR(100) NOT NULL,
    total_amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_orders_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(19, 4) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(19, 4) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payments (
    reference VARCHAR(64) NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    qr_payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6) NULL,
    INDEX idx_payments_status_expires_at (status, expires_at),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payment_callback_events (
    event_id VARCHAR(255) NOT NULL PRIMARY KEY,
    reference VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    error_status INT NOT NULL,
    message VARCHAR(1000) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    INDEX idx_callback_events_reference (reference)
);

CREATE TABLE IF NOT EXISTS dispensing_commands (
    command_id VARCHAR(36) NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    payment_reference VARCHAR(64) NOT NULL,
    machine_id VARCHAR(100) NOT NULL,
    slot_id VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    result_received_at DATETIME(6) NULL,
    manual_review_required BOOLEAN NOT NULL,
    manual_review_reason VARCHAR(1000) NULL,
    CONSTRAINT fk_dispensing_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_dispensing_payment FOREIGN KEY (payment_reference) REFERENCES payments(reference) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS transaction_reviews (
    reference VARCHAR(64) NOT NULL PRIMARY KEY,
    order_id BIGINT NULL,
    reason VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL
);
