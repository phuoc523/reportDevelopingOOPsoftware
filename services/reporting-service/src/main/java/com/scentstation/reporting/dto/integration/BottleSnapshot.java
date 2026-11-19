package com.scentstation.reporting.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BottleSnapshot(
        Long id,
        Long tenantId,
        Long productId,
        Long batchId,
        String bottleCode,
        BigDecimal initialQuantity,
        BigDecimal currentQuantity,
        BigDecimal calibratedConsumptionPerSpray,
        String status
) {
    public BottleSnapshot(Long id, Long tenantId, Long productId, String bottleCode,
            BigDecimal currentQuantity, String status) {
        this(id, tenantId, productId, null, bottleCode, null, currentQuantity, null, status);
    }
}
