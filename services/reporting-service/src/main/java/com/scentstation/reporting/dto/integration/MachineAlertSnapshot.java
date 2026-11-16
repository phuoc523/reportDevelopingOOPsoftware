package com.scentstation.reporting.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MachineAlertSnapshot(
        Long id,
        String type,
        String severity,
        String status,
        Long machineId,
        String locationId
) {
}
