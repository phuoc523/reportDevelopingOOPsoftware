package com.scentstation.reporting.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MachineAlertSnapshot(
        Long id,
        String type,
        String severity,
        String status,
        Long machineId,
        String locationId,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant acknowledgedAt,
        Instant assignedAt,
        Instant resolvedAt
) {
    public MachineAlertSnapshot(Long id, String type, String severity, String status,
            Long machineId, String locationId) {
        this(id, type, severity, status, machineId, locationId, null, null, null, null, null);
    }
}
