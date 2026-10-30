package com.scentstation.order.client;

public record MachineCommandResponse(
        Long orderId,
        String commandId,
        String status,
        String message
) {
}
