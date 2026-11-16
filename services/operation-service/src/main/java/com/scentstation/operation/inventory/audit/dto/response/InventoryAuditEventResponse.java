package com.scentstation.operation.inventory.audit.dto.response;

import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InventoryAuditEventResponse {

    private Long id;
    private Long tenantId;
    private Long actorId;
    private InventoryAuditAction action;
    private String entityType;
    private Long entityId;
    private Long machineId;
    private Long slotId;
    private String beforeValue;
    private String afterValue;
    private String reason;
    private String referenceId;
    private LocalDateTime createdAt;
}
