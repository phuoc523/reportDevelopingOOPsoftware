package com.scentstation.operation.inventory.batch.repository;

import com.scentstation.operation.inventory.batch.entity.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryBatchRepository
        extends JpaRepository<InventoryBatch, Long> {

    // Tìm batch bằng mã trong tenant.
    Optional<InventoryBatch> findByTenantIdAndBatchCode(
            Long tenantId,
            String batchCode
    );
    // Tìm batch theo id trong tenant.
    Optional<InventoryBatch> findByIdAndTenantId(
            Long id,
            Long tenantId
    );
    // Kiểm tra mã batch đã tồn tại trong tenant hay chưa.
    boolean existsByTenantIdAndBatchCode(
            Long tenantId,
            String batchCode
    );

    // Trả toàn bộ batch của tenant.
    List<InventoryBatch> findAllByTenantId(
            Long tenantId
    );

    // Trả batch của tenant theo product.
    List<InventoryBatch> findAllByTenantIdAndProductId(
            Long tenantId,
            Long productId
    );
}
