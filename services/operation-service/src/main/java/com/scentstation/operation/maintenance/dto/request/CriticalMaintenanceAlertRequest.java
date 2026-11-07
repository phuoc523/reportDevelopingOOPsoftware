package com.scentstation.operation.maintenance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CriticalMaintenanceAlertRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotNull
    @Positive
    private Long machineId;

    @Positive
    private Long locationId;

    @Positive
    private Long slotId;

    @NotBlank
    private String alertId;

    @NotBlank
    private String alertType;

    @NotBlank
    private String message;

    @NotNull
    private LocalDateTime occurredAt;
}