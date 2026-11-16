package com.scentstation.reporting.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceTicketSnapshot(Long id, Long tenantId, String status,
        Boolean critical, LocalDateTime dueAt) {}
