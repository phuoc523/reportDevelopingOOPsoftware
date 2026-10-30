package com.scentstation.dto.response;

import com.scentstation.entity.MachineCommandStatus;

public record DispenseCommandResponse(
    String orderId,
    String commandId,
    MachineCommandStatus status,
    String message
) {
    public static DispenseCommandResponse of(String orderId, String commandId,
                                             MachineCommandStatus status, String message) {
        return new DispenseCommandResponse(orderId, commandId, status, message);
    }
}
