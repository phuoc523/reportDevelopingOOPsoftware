CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    fragrance_notes VARCHAR(500) NULL,
    image_url VARCHAR(255) NULL,
    default_price DECIMAL(12, 2) NOT NULL,
    available BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT chk_products_default_price_non_negative
        CHECK (default_price >= 0),
    INDEX idx_products_tenant_id_id (tenant_id, id)
);

CREATE TABLE campaigns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    discount_type ENUM('PERCENTAGE', 'FIXED_AMOUNT') NOT NULL,
    discount_value DECIMAL(12, 2) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_campaigns PRIMARY KEY (id),
    INDEX idx_campaigns_tenant_id_id (tenant_id, id),
    INDEX idx_campaigns_tenant_product (tenant_id, product_id),
    INDEX idx_campaigns_active_window (tenant_id, product_id, active, start_at, end_at)
);
