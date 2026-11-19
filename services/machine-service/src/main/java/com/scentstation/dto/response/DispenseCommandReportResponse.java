package com.scentstation.dto.response;

import com.scentstation.entity.MachineCommand;
import java.time.Instant;

public record DispenseCommandReportResponse(
        String commandId,
        String tenantId,
        Long machineId,
        String slotId,
        String orderId,
        String commandType,
        String status,
        Instant issuedAt,
        Instant acknowledgedAt,
        Instant completedAt,
        Boolean resultSuccess
) {
    public static DispenseCommandReportResponse from(MachineCommand command) {
        return new DispenseCommandReportResponse(command.getCommandId(), command.getTenantId(),
                command.getMachineId(), command.getSlotId(), command.getOrderId(), command.getCommandType(),
                command.getStatus().name(), command.getIssuedAt(), command.getAcknowledgedAt(),
                command.getCompletedAt(), command.getResultSuccess());
    }
}
