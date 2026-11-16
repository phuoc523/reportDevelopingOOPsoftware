package com.scentstation.operation.inventory.refill.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class RefillBottleRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotNull
    @Positive
    private Long bottleId;

    @NotNull
    @DecimalMin(value = "0.001")
    private BigDecimal refillQuantity;

    @NotNull
    @Positive
    private Long actorId;

    @Size(max = 500)
    private String reason;
}