package com.scentstation.operation.inventory.bottle.dto.request;

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
public class CreateBottleRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotNull
    @Positive
    private Long productId;

    @NotNull
    @Positive
    private Long batchId;

    @NotBlank
    private String bottleCode;

    @NotNull
    @DecimalMin(value = "0.001")
    private BigDecimal initialQuantity;
}