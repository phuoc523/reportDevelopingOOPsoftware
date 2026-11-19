CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reference VARCHAR(100) NOT NULL UNIQUE,
    order_id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100),
    machine_id VARCHAR(100),
    slot_id VARCHAR(100),
    product_id BIGINT,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL,
    paid_at DATETIME,
    INDEX idx_transaction_order_id (order_id),
    INDEX idx_transaction_status (status),
    INDEX idx_transaction_created_at (created_at),
    INDEX idx_transaction_paid_at (paid_at),
    INDEX idx_transaction_tenant_created (tenant_id, created_at),
    INDEX idx_transaction_machine (machine_id),
    INDEX idx_transaction_product (product_id)
);

SET @reporting_tenant_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'transactions' AND column_name = 'tenant_id');
SET @reporting_tenant_migration = IF(@reporting_tenant_exists = 0,
    'ALTER TABLE transactions ADD COLUMN tenant_id VARCHAR(100) NULL', 'SELECT 1');
PREPARE reporting_tenant_statement FROM @reporting_tenant_migration;
EXECUTE reporting_tenant_statement;
DEALLOCATE PREPARE reporting_tenant_statement;

SET @reporting_machine_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'transactions' AND column_name = 'machine_id');
SET @reporting_machine_migration = IF(@reporting_machine_exists = 0,
    'ALTER TABLE transactions ADD COLUMN machine_id VARCHAR(100) NULL', 'SELECT 1');
PREPARE reporting_machine_statement FROM @reporting_machine_migration;
EXECUTE reporting_machine_statement;
DEALLOCATE PREPARE reporting_machine_statement;

SET @reporting_slot_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'transactions' AND column_name = 'slot_id');
SET @reporting_slot_migration = IF(@reporting_slot_exists = 0,
    'ALTER TABLE transactions ADD COLUMN slot_id VARCHAR(100) NULL', 'SELECT 1');
PREPARE reporting_slot_statement FROM @reporting_slot_migration;
EXECUTE reporting_slot_statement;
DEALLOCATE PREPARE reporting_slot_statement;

SET @reporting_product_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'transactions' AND column_name = 'product_id');
SET @reporting_product_migration = IF(@reporting_product_exists = 0,
    'ALTER TABLE transactions ADD COLUMN product_id BIGINT NULL', 'SELECT 1');
PREPARE reporting_product_statement FROM @reporting_product_migration;
EXECUTE reporting_product_statement;
DEALLOCATE PREPARE reporting_product_statement;
