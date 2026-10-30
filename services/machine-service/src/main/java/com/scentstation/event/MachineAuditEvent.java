package com.scentstation.event;

import java.time.Instant;

/** Domain event that an adapter can forward to the shared Audit service/message broker. */
public class MachineAuditEvent {
    private final Long machineId;
    private final String action;
    private final String actorId;
    private final String details;
    private final Instant occurredAt;

    public MachineAuditEvent(Long machineId, String action, String actorId, String details,
                             Instant occurredAt) {
        this.machineId = machineId;
        this.action = action;
        this.actorId = actorId;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public Long getMachineId() {
        return machineId;
    }

    public String getAction() {
        return action;
    }

    public String getActorId() {
        return actorId;
    }

    public String getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
