CREATE TABLE IF NOT EXISTS sepay_credit_events (
    transaction_id BIGINT NOT NULL PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL,
    bank_reference VARCHAR(64) NULL,
    transfer_code VARCHAR(64) NULL,
    transfer_content VARCHAR(1024) NULL,
    amount DECIMAL(19, 4) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    gateway VARCHAR(40) NOT NULL,
    transaction_date VARCHAR(19) NOT NULL,
    raw_payload TEXT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_sepay_bank_reference UNIQUE (account_number, bank_reference)
);
