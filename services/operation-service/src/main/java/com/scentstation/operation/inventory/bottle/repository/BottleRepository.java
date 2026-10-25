package com.scentstation.operation.inventory.bottle.repository;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository 
public interface BottleRepository extends JpaRepository<Bottle, Long> {
    Optional<Bottle>findByIdAndTenantId(Long id, Long tenantId);
    Optional<Bottle> findByTenantIdAndBottleCode(Long tenantId, String bottleCode);
    Boolean existsByTenantIdAndBottleCode(Long tenantId, String bottleCode);
    List<Bottle>findAllByTenantId(Long tenantId);
    List<Bottle>findAllByTenantIdAndBatchId(Long tenantId, Long batchId);
    List<Bottle>findAllByTenantIdAndProductId(Long tenantId, Long productId);
    List<Bottle>findAllByTenantIdAndStatus(Long tenantId, BottleStatus status);
}
