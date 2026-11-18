package com.scentstation.operation.event;

import java.time.LocalDateTime;

public record DispenseCompletedEvent(
    int eventVersion,
    String eventId,
    String commandId,
    Long tenantId,
    Long machineId,
    Long slotId,
    String dispenseType,
    String result,
    LocalDateTime occurredAt
) {
}
