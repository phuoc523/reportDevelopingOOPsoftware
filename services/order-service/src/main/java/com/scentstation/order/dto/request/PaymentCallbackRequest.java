package com.scentstation.order.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentCallbackRequest(
        @NotBlank @Size(max = 100) String eventId,
        @NotBlank @Size(max = 100) String reference,
        @NotNull Long orderId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotBlank @Pattern(regexp = "PAID|FAILED") String status
) {
}
