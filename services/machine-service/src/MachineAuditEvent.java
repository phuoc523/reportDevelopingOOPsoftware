package com.scentstation;
import java.time.Instant;
/** Domain event that an adapter can forward to the shared Audit service/message broker. */
public record MachineAuditEvent(Long machineId, String action, String actorId, String details, Instant occurredAt) {}
