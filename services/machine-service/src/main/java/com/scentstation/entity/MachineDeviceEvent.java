package com.scentstation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "machine_device_events",
    uniqueConstraints = @UniqueConstraint(name = "uk_device_event_message", columnNames = "message_id"),
    indexes = @Index(name = "idx_device_event_machine_time", columnList = "machine_id, occurred_at"))
public class MachineDeviceEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "details", nullable = false, length = 500)
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected MachineDeviceEvent() {
    }

    public MachineDeviceEvent(Long machineId, String messageId, String eventType, String details,
                              Instant occurredAt, Instant receivedAt) {
        this.machineId = machineId;
        this.messageId = messageId;
        this.eventType = eventType;
        this.details = details == null ? "" : details;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public Long getMachineId() { return machineId; }
    public String getMessageId() { return messageId; }
    public String getEventType() { return eventType; }
    public String getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
}
