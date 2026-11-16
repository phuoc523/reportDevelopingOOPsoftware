CREATE TABLE firebase_delivery_retry_tokens (
    job_id BIGINT NOT NULL,
    firebase_token VARCHAR(512) NOT NULL,
    CONSTRAINT fk_firebase_retry_token_job
        FOREIGN KEY (job_id) REFERENCES firebase_delivery_jobs(id) ON DELETE CASCADE
);

CREATE INDEX idx_firebase_retry_token_job ON firebase_delivery_retry_tokens(job_id);
