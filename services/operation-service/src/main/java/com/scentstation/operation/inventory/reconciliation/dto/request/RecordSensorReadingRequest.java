package com.scentstation.operation.inventory.reconciliation.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordSensorReadingRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotBlank
    private String sensorEventId;

    @NotNull
    @Positive
    private Long bottleId;

    @NotNull
    @Positive
    private Long machineId;

    @NotNull
    @Positive
    private Long slotId;

    @NotNull
    @DecimalMin(value = "0.000")
    private BigDecimal sensorQuantity;

    @NotNull
    private LocalDateTime measuredAt;
}