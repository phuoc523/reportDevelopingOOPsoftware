ALTER TABLE maintenance_tickets
    ADD COLUMN approved BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN approved_by_actor_id BIGINT NULL,
    ADD COLUMN approved_at DATETIME(6) NULL;
