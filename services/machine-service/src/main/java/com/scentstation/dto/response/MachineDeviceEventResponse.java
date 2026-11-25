package com.scentstation.dto.response;

import com.scentstation.entity.MachineDeviceEvent;
import java.time.Instant;

public record MachineDeviceEventResponse(
    Long id,
    Long machineId,
    String messageId,
    String eventType,
    String details,
    Instant occurredAt,
    Instant receivedAt
) {
    public static MachineDeviceEventResponse from(MachineDeviceEvent event) {
        return new MachineDeviceEventResponse(
            event.getId(),
            event.getMachineId(),
            event.getMessageId(),
            event.getEventType(),
            event.getDetails(),
            event.getOccurredAt(),
            event.getReceivedAt()
        );
    }
}
