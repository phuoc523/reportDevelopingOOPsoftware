package com.scentstation.order.client;

public record InstalledDispenseRequest(
        Long tenantId,
        String commandId,
        Long machineId,
        Long slotId,
        String dispenseType,
        String result
) {
}
