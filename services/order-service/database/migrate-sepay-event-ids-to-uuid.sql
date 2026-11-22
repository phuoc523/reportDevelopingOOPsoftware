-- Run only after taking and verifying a MySQL backup.
-- Required when upgrading an existing installation whose transaction_id is BIGINT PRIMARY KEY.
-- The application's schema init does not alter an already-created table.
ALTER TABLE sepay_credit_events
    DROP PRIMARY KEY,
    MODIFY transaction_id VARCHAR(64) NOT NULL,
    ADD COLUMN event_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
    ADD CONSTRAINT uq_sepay_transaction_id UNIQUE (transaction_id);
