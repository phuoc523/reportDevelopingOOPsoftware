package com.scentstation.dto.response;

public record OrderMachineSlotResponse(
        Long machineId,
        String tenantId,
        Long slotId,
        String productId,
        boolean machineAvailable,
        boolean slotAvailable,
        String reason
) {}
