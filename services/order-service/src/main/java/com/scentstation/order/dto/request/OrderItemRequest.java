package com.scentstation.order.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record OrderItemRequest(
        @NotNull @Positive Long productId,
        @NotBlank String productName,
        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @Positive int quantity
) {
}
