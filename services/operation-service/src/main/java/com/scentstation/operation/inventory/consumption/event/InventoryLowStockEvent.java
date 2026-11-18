package com.scentstation.operation.inventory.consumption.event;

import java.math.BigDecimal;

public record InventoryLowStockEvent(
        Long tenantId,
        String commandId,
        Long machineId,
        Long slotId,
        Long bottleId,
        BigDecimal remainingQuantity,
        long estimatedRemainingSprays
) {
}
