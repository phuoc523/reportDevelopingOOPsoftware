package com.scentstation.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PaymentReconciliationRequest(
        @NotNull @Size(max = 1000) List<@NotNull @Valid SimulatedPaymentRecord> payments
) {
}
