package com.scentstation.reporting.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceTicketSnapshot(Long id, Long tenantId, Long machineId, Long locationId,
        Long slotId, Long bottleId, String category, String severity, String priority, String status,
        Long assignedToActorId, Boolean critical, LocalDateTime dueAt, LocalDateTime createdAt,
        LocalDateTime updatedAt, LocalDateTime closedAt) {
    public MaintenanceTicketSnapshot(Long id, Long tenantId, String status,
            Boolean critical, LocalDateTime dueAt) {
        this(id, tenantId, null, null, null, null, null, null, null, status,
                null, critical, dueAt, null, null, null);
    }
}
