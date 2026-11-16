package com.scentstation.order.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record OrderItemRequest(
        @Schema(description = "ID of the product", example = "3")
        @NotNull @Positive Long productId,
        @Schema(description = "Product name snapshot stored with the order", example = "Scent Sample")
        @NotBlank String productName,
        @Schema(description = "Unit price, greater than zero, with at most two decimal places", example = "12.75")
        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @Schema(description = "Number of units to order; must be positive", example = "2")
        @Positive int quantity
) {
}
