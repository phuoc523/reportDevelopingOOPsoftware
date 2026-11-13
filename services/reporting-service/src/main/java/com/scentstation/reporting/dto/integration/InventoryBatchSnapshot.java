package com.scentstation.reporting.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryBatchSnapshot(Long id, Long tenantId, Long productId,
        Integer receivedQuantity, LocalDate expiryDate, Boolean expired) {}
