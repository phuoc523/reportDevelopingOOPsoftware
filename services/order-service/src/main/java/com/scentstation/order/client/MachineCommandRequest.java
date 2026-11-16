package com.scentstation.order.client;

public record MachineCommandRequest(
        String tenantId,
        Long orderId,
        String paymentReference,
        String machineId,
        String slotId,
        String commandId
) {
}
