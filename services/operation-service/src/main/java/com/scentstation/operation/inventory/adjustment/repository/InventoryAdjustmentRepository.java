package com.scentstation.operation.inventory.adjustment.repository;

import com.scentstation.operation.inventory.adjustment.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryAdjustmentRepository
        extends JpaRepository<InventoryAdjustment, Long> {

    // Trả lịch sử adjustment của bottle.
    List<InventoryAdjustment>
    findAllByTenantIdAndBottleIdOrderByCreatedAtDesc(
            Long tenantId,
            Long bottleId
    );

    // Trả lịch sử adjustment của tenant.
    List<InventoryAdjustment>
    findAllByTenantIdOrderByCreatedAtDesc(
            Long tenantId
    );
}
