package com.scentstation.operation.inventory.audit.repository;

import com.scentstation.operation.inventory.audit.entity.InventoryAuditEvent;
import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryAuditEventRepository
        extends JpaRepository<InventoryAuditEvent, Long> {

    // Trả audit của tenant theo thời gian giảm dần.
    List<InventoryAuditEvent>
    findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    // Trả audit của một entity trong tenant.
    List<InventoryAuditEvent>
    findAllByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            Long tenantId,
            String entityType,
            Long entityId
    );

    // Lọc audit của tenant theo action.
    List<InventoryAuditEvent>
    findAllByTenantIdAndActionOrderByCreatedAtDesc(
            Long tenantId,
            InventoryAuditAction action
    );
}
