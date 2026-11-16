package com.scentstation.order.client;

public record MachineSlotSnapshot(Long machineId, String tenantId, Long slotId, String productId,
                                  Boolean machineAvailable, Boolean slotAvailable, String reason) {}
