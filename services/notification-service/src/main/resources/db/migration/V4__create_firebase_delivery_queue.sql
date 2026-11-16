CREATE TABLE firebase_delivery_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    source VARCHAR(50) NOT NULL,
    source_event_id VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    last_error_type VARCHAR(200),
    requested_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_firebase_delivery_jobs PRIMARY KEY (id),
    CONSTRAINT uk_firebase_delivery_notification UNIQUE (notification_id),
    CONSTRAINT fk_firebase_delivery_notification FOREIGN KEY (notification_id) REFERENCES notifications(id),
    INDEX idx_firebase_delivery_ready (status, next_attempt_at, id)
);

CREATE TABLE firebase_delivery_recipients (
    job_id BIGINT NOT NULL,
    recipient_id VARCHAR(100) NOT NULL,
    CONSTRAINT fk_firebase_delivery_recipient_job FOREIGN KEY (job_id)
        REFERENCES firebase_delivery_jobs(id) ON DELETE CASCADE,
    CONSTRAINT uk_firebase_delivery_recipient UNIQUE (job_id, recipient_id)
);
