package com.scentstation;

import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.math.BigDecimal;

public final class MachineRequests { private MachineRequests() {}
    public static record CreateMachineRequest(@NotBlank @Size(max=100) String serialNumber, @NotBlank @Size(max=150) String deviceIdentity, @NotBlank @Size(max=100) String tenantId, @NotBlank @Size(max=100) String locationId, @NotBlank @Size(max=150) String name) {}
    public static record CreateSlotRequest(@NotNull @Min(1) Integer slotNumber, @NotBlank @Size(max=50) String slotCode, @Size(max=100) String productId, @Size(max=100) String cartridgeId, @NotNull Boolean enabled, @NotNull @Min(0) Integer stockThreshold, @NotNull @Min(1) @Max(60000) Integer calibratedSprayDurationMs, @Positive Double doseMl, @NotNull @DecimalMin("0.00") BigDecimal price) {}
    public static record MachineControlRequest(@NotNull ControlAction action) { public enum ControlAction { ENABLE, DISABLE, ENTER_MAINTENANCE } }
    public static record UpdateDeviceRegistrationStatusRequest(@NotNull DeviceRegistrationStatus status) {}
    public static record UpdateMachineRequest(@NotBlank @Size(max=100) String tenantId, @NotBlank @Size(max=100) String locationId, @NotBlank @Size(max=150) String name) {}
    public static record UpdateOperatingHoursRequest(@NotNull LocalTime startTime, @NotNull LocalTime endTime) {}
    public static record UpdateOperationalStatusRequest(@NotNull MachineOperationalStatus status) {}
    public static record UpdateSlotEnabledRequest(@NotNull Boolean enabled) {}
    public static record UpdateSlotRequest(@Size(max=100) String productId, @Size(max=100) String cartridgeId, @NotNull Boolean enabled, @NotNull @Min(0) Integer stockThreshold, @NotNull @Min(1) @Max(60000) Integer calibratedSprayDurationMs, @Positive Double doseMl, @NotNull @DecimalMin("0.00") BigDecimal price) {}
}

