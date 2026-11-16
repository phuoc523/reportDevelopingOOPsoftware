package com.scentstation.reporting.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MachineSnapshot(
        Long id,
        String serialNumber,
        String tenantId,
        String locationId,
        String name,
        String operationalStatus
) {
}
