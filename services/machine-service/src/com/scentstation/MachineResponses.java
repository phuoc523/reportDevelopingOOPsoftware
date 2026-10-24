package com.scentstation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public final class MachineResponses {
    private MachineResponses() {
    }

    public static class MachineAvailabilityResponse {
        private final Long machineId;
        private final boolean machineAvailable;
        private final MachineOperationalStatus operationalStatus;
        private final String reason;
        private final List<SlotResponse> slots;

        public MachineAvailabilityResponse(Long machineId, boolean machineAvailable,
                                           MachineOperationalStatus operationalStatus,
                                           String reason, List<SlotResponse> slots) {
            this.machineId = machineId;
            this.machineAvailable = machineAvailable;
            this.operationalStatus = operationalStatus;
            this.reason = reason;
            this.slots = slots;
        }

        public Long getMachineId() {
            return machineId;
        }

        public boolean isMachineAvailable() {
            return machineAvailable;
        }

        public MachineOperationalStatus getOperationalStatus() {
            return operationalStatus;
        }

        public String getReason() {
            return reason;
        }

        public List<SlotResponse> getSlots() {
            return slots;
        }
    }

    public static class MachineHistoryResponse {
        private final Long id;
        private final Long machineId;
        private final MachineHistoryAction action;
        private final String oldTenantId;
        private final String newTenantId;
        private final String oldLocationId;
        private final String newLocationId;
        private final String details;
        private final String actorId;
        private final Instant occurredAt;

        public MachineHistoryResponse(Long id, Long machineId, MachineHistoryAction action,
                                      String oldTenantId, String newTenantId, String oldLocationId,
                                      String newLocationId, String details, String actorId,
                                      Instant occurredAt) {
            this.id = id;
            this.machineId = machineId;
            this.action = action;
            this.oldTenantId = oldTenantId;
            this.newTenantId = newTenantId;
            this.oldLocationId = oldLocationId;
            this.newLocationId = newLocationId;
            this.details = details;
            this.actorId = actorId;
            this.occurredAt = occurredAt;
        }

        public static MachineHistoryResponse from(MachineHistory history) {
            return new MachineHistoryResponse(
                history.getId(),
                history.getMachineId(),
                history.getAction(),
                history.getOldTenantId(),
                history.getNewTenantId(),
                history.getOldLocationId(),
                history.getNewLocationId(),
                history.getDetails(),
                history.getActorId(),
                history.getOccurredAt()
            );
        }

        public Long getId() {
            return id;
        }

        public Long getMachineId() {
            return machineId;
        }

        public MachineHistoryAction getAction() {
            return action;
        }

        public String getOldTenantId() {
            return oldTenantId;
        }

        public String getNewTenantId() {
            return newTenantId;
        }

        public String getOldLocationId() {
            return oldLocationId;
        }

        public String getNewLocationId() {
            return newLocationId;
        }

        public String getDetails() {
            return details;
        }

        public String getActorId() {
            return actorId;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }
    }

    public static class MachineResponse {
        private final Long id;
        private final String serialNumber;
        private final String deviceIdentity;
        private final String tenantId;
        private final String locationId;
        private final String name;
        private final DeviceRegistrationStatus deviceRegistrationStatus;
        private final MachineOperationalStatus operationalStatus;
        private final LocalTime operatingHoursStart;
        private final LocalTime operatingHoursEnd;
        private final Instant lastSeenAt;
        private final Instant createdAt;
        private final Instant updatedAt;

        public MachineResponse(Long id, String serialNumber, String deviceIdentity,
                               String tenantId, String locationId, String name,
                               DeviceRegistrationStatus deviceRegistrationStatus,
                               MachineOperationalStatus operationalStatus,
                               LocalTime operatingHoursStart, LocalTime operatingHoursEnd,
                               Instant lastSeenAt, Instant createdAt, Instant updatedAt) {
            this.id = id;
            this.serialNumber = serialNumber;
            this.deviceIdentity = deviceIdentity;
            this.tenantId = tenantId;
            this.locationId = locationId;
            this.name = name;
            this.deviceRegistrationStatus = deviceRegistrationStatus;
            this.operationalStatus = operationalStatus;
            this.operatingHoursStart = operatingHoursStart;
            this.operatingHoursEnd = operatingHoursEnd;
            this.lastSeenAt = lastSeenAt;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public static MachineResponse from(Machine machine) {
            return new MachineResponse(
                machine.getId(),
                machine.getSerialNumber(),
                machine.getDeviceIdentity(),
                machine.getTenantId(),
                machine.getLocationId(),
                machine.getName(),
                machine.getDeviceRegistrationStatus(),
                machine.getOperationalStatus(),
                machine.getOperatingHoursStart(),
                machine.getOperatingHoursEnd(),
                machine.getLastSeenAt(),
                machine.getCreatedAt(),
                machine.getUpdatedAt()
            );
        }

        public Long getId() {
            return id;
        }

        public String getSerialNumber() {
            return serialNumber;
        }

        public String getDeviceIdentity() {
            return deviceIdentity;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getLocationId() {
            return locationId;
        }

        public String getName() {
            return name;
        }

        public DeviceRegistrationStatus getDeviceRegistrationStatus() {
            return deviceRegistrationStatus;
        }

        public MachineOperationalStatus getOperationalStatus() {
            return operationalStatus;
        }

        public LocalTime getOperatingHoursStart() {
            return operatingHoursStart;
        }

        public LocalTime getOperatingHoursEnd() {
            return operatingHoursEnd;
        }

        public Instant getLastSeenAt() {
            return lastSeenAt;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }
    }

    public static class SlotResponse {
        private final Long id;
        private final Long machineId;
        private final Integer slotNumber;
        private final String slotCode;
        private final String productId;
        private final String cartridgeId;
        private final boolean enabled;
        private final SlotStatus status;
        private final boolean available;
        private final String availabilityReason;
        private final Integer stockThreshold;
        private final Integer calibratedSprayDurationMs;
        private final Double doseMl;
        private final BigDecimal price;
        private final Instant createdAt;
        private final Instant updatedAt;

        public SlotResponse(Long id, Long machineId, Integer slotNumber, String slotCode,
                            String productId, String cartridgeId, boolean enabled,
                            SlotStatus status, boolean available, String availabilityReason,
                            Integer stockThreshold,
                            Integer calibratedSprayDurationMs, Double doseMl, BigDecimal price,
                            Instant createdAt, Instant updatedAt) {
            this.id = id;
            this.machineId = machineId;
            this.slotNumber = slotNumber;
            this.slotCode = slotCode;
            this.productId = productId;
            this.cartridgeId = cartridgeId;
            this.enabled = enabled;
            this.status = status;
            this.available = available;
            this.availabilityReason = availabilityReason;
            this.stockThreshold = stockThreshold;
            this.calibratedSprayDurationMs = calibratedSprayDurationMs;
            this.doseMl = doseMl;
            this.price = price;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public static SlotResponse from(MachineSlot slot, boolean available, String availabilityReason) {
            return new SlotResponse(
                slot.getId(),
                slot.getMachineId(),
                slot.getSlotNumber(),
                slot.getSlotCode(),
                slot.getProductId(),
                slot.getCartridgeId(),
                slot.isEnabled(),
                slot.getStatus(),
                available,
                availabilityReason,
                slot.getStockThreshold(),
                slot.getCalibratedSprayDurationMs(),
                slot.getDoseMl(),
                slot.getPrice(),
                slot.getCreatedAt(),
                slot.getUpdatedAt()
            );
        }

        public Long getId() {
            return id;
        }

        public Long getMachineId() {
            return machineId;
        }

        public Integer getSlotNumber() {
            return slotNumber;
        }

        public String getSlotCode() {
            return slotCode;
        }

        public String getProductId() {
            return productId;
        }

        public String getCartridgeId() {
            return cartridgeId;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public SlotStatus getStatus() {
            return status;
        }

        public boolean isAvailable() {
            return available;
        }

        public String getAvailabilityReason() {
            return availabilityReason;
        }

        public Integer getStockThreshold() {
            return stockThreshold;
        }

        public Integer getCalibratedSprayDurationMs() {
            return calibratedSprayDurationMs;
        }

        public Double getDoseMl() {
            return doseMl;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }
    }
}
