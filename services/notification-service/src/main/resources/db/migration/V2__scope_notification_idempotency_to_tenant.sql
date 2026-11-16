ALTER TABLE notifications DROP INDEX uk_notifications_source_event;

ALTER TABLE notifications
    ADD CONSTRAINT uk_notifications_source_event
    UNIQUE (tenant_id, source, source_event_id);
