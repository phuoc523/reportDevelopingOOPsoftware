package com.scentstation.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "dispense_event_outbox")
public class DispenseEventOutbox {
    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "command_id", nullable = false, length = 36)
    private String commandId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "dispense_type", nullable = false, length = 32)
    private String dispenseType;

    @Column(name = "dispense_result", nullable = false, length = 32)
    private String result;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DispenseEventOutbox() {
    }

    public DispenseEventOutbox(String commandId, Long tenantId, Long machineId, Long slotId) {
        this.eventId = "dispense:" + commandId;
        this.eventVersion = 1;
        this.commandId = commandId;
        this.tenantId = tenantId;
        this.machineId = machineId;
        this.slotId = slotId;
        this.dispenseType = "CUSTOMER";
        this.result = "SUCCESS";
        this.occurredAt = LocalDateTime.now();
        this.createdAt = occurredAt;
        this.nextAttemptAt = occurredAt;
    }

    public void attempted(LocalDateTime retryAt) {
        attemptCount++;
        nextAttemptAt = retryAt;
    }

    public String getEventId() { return eventId; }
    public int getEventVersion() { return eventVersion; }
    public String getCommandId() { return commandId; }
    public Long getTenantId() { return tenantId; }
    public Long getMachineId() { return machineId; }
    public Long getSlotId() { return slotId; }
    public String getDispenseType() { return dispenseType; }
    public String getResult() { return result; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
}
