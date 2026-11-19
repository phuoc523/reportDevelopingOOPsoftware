package com.scentstation.reporting.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryMovementSnapshot(Long id, Long tenantId, Long bottleId, String movementType,
        Long machineId, Long slotId, BigDecimal quantityBefore, BigDecimal quantityAfter,
        BigDecimal difference, Long actorId, String reason, String referenceId, LocalDateTime createdAt) {
    public InventoryMovementSnapshot(Long id, Long tenantId, String movementType,
            BigDecimal quantityBefore, BigDecimal quantityAfter, String referenceId, LocalDateTime createdAt) {
        this(id, tenantId, null, movementType, null, null, quantityBefore, quantityAfter,
                null, null, null, referenceId, createdAt);
    }
}
