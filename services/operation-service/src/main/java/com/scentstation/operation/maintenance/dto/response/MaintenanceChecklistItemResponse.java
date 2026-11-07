package com.scentstation.operation.maintenance.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MaintenanceChecklistItemResponse {

    private Long id;

    private Long tenantId;

    private Long ticketId;

    private String itemName;

    private String description;

    private boolean required;

    private boolean completed;

    private String resultNote;

    private Long completedByActorId;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;
}