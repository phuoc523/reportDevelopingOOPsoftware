package com.scentstation.operation.inventory.adjustment.repository;

import com.scentstation.operation.inventory.adjustment.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryAdjustmentRepository
        extends JpaRepository<InventoryAdjustment, Long> {

    List<InventoryAdjustment>
    findAllByTenantIdAndBottleIdOrderByCreatedAtDesc(
            Long tenantId,
            Long bottleId
    );

    List<InventoryAdjustment>
    findAllByTenantIdOrderByCreatedAtDesc(
            Long tenantId
    );
}