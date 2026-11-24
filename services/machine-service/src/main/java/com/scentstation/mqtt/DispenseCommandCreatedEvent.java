package com.scentstation.mqtt;

import java.time.Instant;

public record DispenseCommandCreatedEvent(
    String commandId,
    long machineId,
    String slotId,
    int durationMs,
    Instant issuedAt,
    Instant expiresAt
) {
}
