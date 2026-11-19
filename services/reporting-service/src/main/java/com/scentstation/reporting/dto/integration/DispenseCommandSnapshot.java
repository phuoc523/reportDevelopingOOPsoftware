package com.scentstation.reporting.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DispenseCommandSnapshot(
        String commandId,
        String tenantId,
        Long machineId,
        String slotId,
        String orderId,
        String commandType,
        String status,
        Instant issuedAt,
        Instant acknowledgedAt,
        Instant completedAt,
        Boolean resultSuccess
) {}
