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
