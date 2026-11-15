CREATE TABLE firebase_device_registrations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    device_id VARCHAR(200) NOT NULL,
    firebase_token VARCHAR(512) NULL,
    platform VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_firebase_device_registrations PRIMARY KEY (id),
    CONSTRAINT uk_firebase_device_owner UNIQUE (tenant_id, user_id, device_id),
    CONSTRAINT uk_firebase_token UNIQUE (firebase_token),
    INDEX idx_firebase_active_user (tenant_id, user_id, active)
);
