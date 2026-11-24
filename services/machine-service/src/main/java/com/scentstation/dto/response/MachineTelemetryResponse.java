package com.scentstation.dto.response;

import com.scentstation.entity.MachineTelemetryLatest;
import java.math.BigDecimal;
import java.time.Instant;

public record MachineTelemetryResponse(
    Long machineId,
    Long slotId,
    BigDecimal remainingPercent,
    String sensorStatus,
    Instant measuredAt,
    Instant receivedAt
) {
    public static MachineTelemetryResponse from(MachineTelemetryLatest snapshot) {
        return new MachineTelemetryResponse(
            snapshot.getMachineId(),
            snapshot.getSlotId(),
            snapshot.getRemainingPercent(),
            snapshot.getSensorStatus(),
            snapshot.getMeasuredAt(),
            snapshot.getReceivedAt()
        );
    }
}
