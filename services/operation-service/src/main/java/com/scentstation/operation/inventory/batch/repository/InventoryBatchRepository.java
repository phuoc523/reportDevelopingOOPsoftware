package com.scentstation.operation.inventory.batch.repository;

import com.scentstation.operation.inventory.batch.entity.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryBatchRepository
        extends JpaRepository<InventoryBatch, Long> {

    Optional<InventoryBatch> findByTenantIdAndBatchCode(
            Long tenantId,
            String batchCode
    );
    Optional<InventoryBatch> findByIdAndTenantId(
            Long id,
            Long tenantId
    );
    boolean existsByTenantIdAndBatchCode(
            Long tenantId,
            String batchCode
    );

    List<InventoryBatch> findAllByTenantId(
            Long tenantId
    );

    List<InventoryBatch> findAllByTenantIdAndProductId(
            Long tenantId,
            Long productId
    );
}