package com.scentstation.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicPaymentLookupRequest(
        @NotBlank
        @Size(max = 128)
        String paymentReference
) {
}
