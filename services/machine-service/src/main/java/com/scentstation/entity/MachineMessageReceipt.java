package com.scentstation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "machine_message_receipts", indexes = {
    @Index(name = "idx_message_receipt_machine_time", columnList = "machine_id, received_at")
})
public class MachineMessageReceipt {
    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected MachineMessageReceipt() {
    }

    public MachineMessageReceipt(String eventId, Long machineId, String messageType) {
        this.eventId = eventId;
        this.machineId = machineId;
        this.messageType = messageType;
        this.receivedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public Long getMachineId() { return machineId; }
    public String getMessageType() { return messageType; }
    public Instant getReceivedAt() { return receivedAt; }
}
