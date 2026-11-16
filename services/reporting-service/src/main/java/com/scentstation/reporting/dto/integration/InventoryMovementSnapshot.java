package com.scentstation.reporting.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryMovementSnapshot(Long id, Long tenantId, String movementType,
        BigDecimal quantityBefore, BigDecimal quantityAfter, String referenceId, LocalDateTime createdAt) {}
