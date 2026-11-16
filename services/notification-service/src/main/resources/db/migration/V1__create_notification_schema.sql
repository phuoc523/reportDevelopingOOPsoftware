CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    source VARCHAR(50) NOT NULL,
    source_event_id VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT uk_notifications_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_notifications_source_event UNIQUE (source, source_event_id),
    INDEX idx_notifications_tenant_created (tenant_id, created_at, id)
);

CREATE TABLE notification_recipients (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(100) NOT NULL,
    notification_id BIGINT NOT NULL,
    recipient_id VARCHAR(100) NOT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_notification_recipients PRIMARY KEY (id),
    CONSTRAINT uk_notification_recipient UNIQUE (notification_id, recipient_id),
    CONSTRAINT fk_notification_recipient_tenant
        FOREIGN KEY (tenant_id, notification_id)
        REFERENCES notifications (tenant_id, id),
    INDEX idx_notification_recipient_inbox
        (tenant_id, recipient_id, read_at, created_at, id)
);
