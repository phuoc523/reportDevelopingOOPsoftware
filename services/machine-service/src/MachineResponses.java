package com.scentstation;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.math.BigDecimal;

public final class MachineResponses { private MachineResponses() {}
    public static record MachineAvailabilityResponse(Long machineId, boolean machineAvailable, MachineOperationalStatus operationalStatus, String reason, List<SlotResponse> slots) {}
    public static record MachineHistoryResponse(Long id, Long machineId, MachineHistoryAction action, String oldTenantId, String newTenantId, String oldLocationId, String newLocationId, String details, String actorId, Instant occurredAt) { public static MachineHistoryResponse from(MachineHistory h) { return new MachineHistoryResponse(h.getId(), h.getMachineId(), h.getAction(), h.getOldTenantId(), h.getNewTenantId(), h.getOldLocationId(), h.getNewLocationId(), h.getDetails(), h.getActorId(), h.getOccurredAt()); } }
    public static record MachineResponse(Long id, String serialNumber, String deviceIdentity, String tenantId, String locationId, String name, DeviceRegistrationStatus deviceRegistrationStatus, MachineOperationalStatus operationalStatus, LocalTime operatingHoursStart, LocalTime operatingHoursEnd, Instant lastSeenAt, Instant createdAt, Instant updatedAt) { public static MachineResponse from(Machine m) { return new MachineResponse(m.getId(), m.getSerialNumber(), m.getDeviceIdentity(), m.getTenantId(), m.getLocationId(), m.getName(), m.getDeviceRegistrationStatus(), m.getOperationalStatus(), m.getOperatingHoursStart(), m.getOperatingHoursEnd(), m.getLastSeenAt(), m.getCreatedAt(), m.getUpdatedAt()); } }
    public static record SlotResponse(Long id, Long machineId, Integer slotNumber, String slotCode, String productId, String cartridgeId, boolean enabled, SlotStatus status, boolean available, Integer stockThreshold, Integer calibratedSprayDurationMs, Double doseMl, BigDecimal price, Instant createdAt, Instant updatedAt) { public static SlotResponse from(MachineSlot slot, boolean available) { return new SlotResponse(slot.getId(), slot.getMachineId(), slot.getSlotNumber(), slot.getSlotCode(), slot.getProductId(), slot.getCartridgeId(), slot.isEnabled(), slot.getStatus(), available, slot.getStockThreshold(), slot.getCalibratedSprayDurationMs(), slot.getDoseMl(), slot.getPrice(), slot.getCreatedAt(), slot.getUpdatedAt()); } }
}
