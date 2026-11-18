package com.scentstation.catalog.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CommitRetailSaleRequest(
        @NotNull @Positive Long orderId,
        @NotNull @Positive Integer quantity
) { }
