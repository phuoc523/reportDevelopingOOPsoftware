package com.scentstation.operation.inventory.consumption.dto.request;

import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.enums.DispenseType;
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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordDispenseRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotBlank
    private String commandId;

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
    private DispenseType dispenseType;

    @NotNull
    private DispenseResult result;

    @NotNull
    @DecimalMin(value = "0.001")
    private BigDecimal consumedQuantity;
}