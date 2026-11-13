CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reference VARCHAR(100) NOT NULL UNIQUE,
    order_id VARCHAR(100) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL,
    paid_at DATETIME,
    INDEX idx_transaction_order_id (order_id),
    INDEX idx_transaction_status (status),
    INDEX idx_transaction_created_at (created_at),
    INDEX idx_transaction_paid_at (paid_at)
);
