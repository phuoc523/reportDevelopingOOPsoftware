package com.scentstation.notification.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record MachineAlertEvent(
    @NotNull @Min(1) @Max(1) Integer eventVersion,
    @NotBlank @Size(max = 100) String eventId,
    @NotNull @Positive Long alertId,
    @NotBlank @Size(max = 100) String tenantId,
    @NotNull @Positive Long machineId,
    @NotBlank @Size(max = 100) String locationId,
    @NotBlank @Pattern(regexp = "LOW_STOCK|MACHINE_OFFLINE|DOOR_OPEN|LEAK_DETECTED|SENSOR_FAILURE|ACTUATOR_FAILURE|DISPENSE_FAILED|SLOT_EMPTY") String alertType,
    @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String severity,
    @NotBlank @Pattern(regexp = "OPEN|ACKNOWLEDGED|ASSIGNED|ESCALATED|RESOLVED") String status,
    @NotNull Instant occurredAt,
    @NotBlank @Pattern(regexp = "machine-service") String source
) {
}
