package com.scentstation.reporting.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BottleSnapshot(
        Long id,
        Long tenantId,
        Long productId,
        String bottleCode,
        BigDecimal currentQuantity,
        String status
) {
}
