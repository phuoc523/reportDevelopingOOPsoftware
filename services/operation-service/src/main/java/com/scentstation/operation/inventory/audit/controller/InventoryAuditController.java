package com.scentstation.operation.inventory.audit.controller;

import com.scentstation.operation.inventory.audit.dto.response.InventoryAuditEventResponse;
import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.audit.service.InventoryAuditService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/audit-events")
@RequiredArgsConstructor
@Validated
public class InventoryAuditController {

    private final InventoryAuditService auditService;

    // Trả audit của tenant và hỗ trợ lọc tùy chọn theo action.
    @GetMapping
    public ResponseEntity<List<InventoryAuditEventResponse>> getHistory(
            @RequestParam @Positive Long tenantId,
            @RequestParam(required = false) InventoryAuditAction action
    ) {
        if (action != null) {
            return ResponseEntity.ok(
                    auditService.getHistoryByAction(tenantId, action)
            );
        }

        return ResponseEntity.ok(auditService.getTenantHistory(tenantId));
    }

    // Trả audit của một entity cụ thể trong phạm vi tenant.
    @GetMapping("/entities/{entityType}/{entityId}")
    public ResponseEntity<List<InventoryAuditEventResponse>> getEntityHistory(
            @PathVariable @NotBlank String entityType,
            @PathVariable @Positive Long entityId,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                auditService.getEntityHistory(
                        tenantId,
                        entityType,
                        entityId
                )
        );
    }
}
