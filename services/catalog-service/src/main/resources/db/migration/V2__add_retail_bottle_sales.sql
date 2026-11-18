ALTER TABLE products
    ADD COLUMN bottle_price DECIMAL(12, 2) NULL AFTER default_price,
    ADD COLUMN bottle_volume_ml INT NULL AFTER bottle_price,
    ADD COLUMN bottle_stock INT NOT NULL DEFAULT 0 AFTER bottle_volume_ml,
    ADD CONSTRAINT chk_products_bottle_price_positive CHECK (bottle_price IS NULL OR bottle_price > 0),
    ADD CONSTRAINT chk_products_bottle_volume_positive CHECK (bottle_volume_ml IS NULL OR bottle_volume_ml > 0),
    ADD CONSTRAINT chk_products_bottle_stock_non_negative CHECK (bottle_stock >= 0);

CREATE TABLE retail_sale_commits (
    order_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    committed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_retail_sale_commits PRIMARY KEY (order_id),
    CONSTRAINT chk_retail_sale_quantity_positive CHECK (quantity > 0),
    INDEX idx_retail_sales_tenant_product (tenant_id, product_id)
);

UPDATE products SET bottle_price = 2450000.00, bottle_volume_ml = 100, bottle_stock = 6 WHERE id = 91001;
UPDATE products SET bottle_price = 1890000.00, bottle_volume_ml = 100, bottle_stock = 8 WHERE id = 91006;
UPDATE products SET bottle_price = 2150000.00, bottle_volume_ml = 100, bottle_stock = 5 WHERE id = 91007;
UPDATE products SET bottle_price = 2350000.00, bottle_volume_ml = 100, bottle_stock = 2 WHERE id = 91008;
UPDATE products SET bottle_price = 3450000.00, bottle_volume_ml = 100, bottle_stock = 4 WHERE id = 91002;
UPDATE products SET bottle_price = 3950000.00, bottle_volume_ml = 100, bottle_stock = 4 WHERE id = 91003;
UPDATE products SET bottle_price = 3250000.00, bottle_volume_ml = 100, bottle_stock = 4 WHERE id = 91004;
UPDATE products SET bottle_price = 3750000.00, bottle_volume_ml = 100, bottle_stock = 0 WHERE id = 91005;
