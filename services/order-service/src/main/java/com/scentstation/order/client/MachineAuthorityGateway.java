package com.scentstation.order.client;

public interface MachineAuthorityGateway {
    MachineSlotSnapshot getSlot(long tenantId, long machineId, long slotId);
}
