package com.scentstation.operation.inventory.bottle.repository;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository 
public interface BottleRepository extends JpaRepository<Bottle, Long> {
    // Tìm bottle theo id trong tenant.
    Optional<Bottle>findByIdAndTenantId(Long id, Long tenantId);
    // Tìm bottle bằng mã duy nhất trong tenant.
    Optional<Bottle> findByTenantIdAndBottleCode(Long tenantId, String bottleCode);
    // Kiểm tra mã bottle đã tồn tại trong tenant hay chưa.
    Boolean existsByTenantIdAndBottleCode(Long tenantId, String bottleCode);
    // Trả toàn bộ bottle của tenant.
    List<Bottle>findAllByTenantId(Long tenantId);
    // Trả bottle của tenant theo batch.
    List<Bottle>findAllByTenantIdAndBatchId(Long tenantId, Long batchId);
    // Trả bottle của tenant theo product.
    List<Bottle>findAllByTenantIdAndProductId(Long tenantId, Long productId);
    // Trả bottle của tenant theo trạng thái.
    List<Bottle>findAllByTenantIdAndStatus(Long tenantId, BottleStatus status);
}
