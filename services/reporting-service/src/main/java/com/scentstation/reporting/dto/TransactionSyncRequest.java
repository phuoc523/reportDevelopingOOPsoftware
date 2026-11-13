
package com.scentstation.reporting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionSyncRequest(

        @NotBlank(message = "Reference is required")
        @Size(max = 100)
        String reference,

        @NotBlank(message = "Order ID is required")
        @Size(max = 100)
        String orderId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Size(max = 10)
        String currency,

        @NotBlank(message = "Status is required")
        String status,

        @NotNull(message = "Created time is required")
        LocalDateTime createdAt,

        LocalDateTime paidAt

) {
}
