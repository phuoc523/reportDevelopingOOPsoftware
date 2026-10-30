package com.scentstation.operation.inventory.movement.repository;

import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository 
public interface BottleMovementRepository extends JpaRepository<BottleMovement, Long> {
    // Trả movement của tenant theo thời gian giảm dần.
    List<BottleMovement> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    // Trả movement của một bottle trong tenant.
    List<BottleMovement> findAllByTenantIdAndBottleIdOrderByCreatedAtDesc(Long tenantId,Long bottleId);

    // Trả movement của một machine trong tenant.
    List<BottleMovement> findAllByTenantIdAndMachineIdOrderByCreatedAtDesc(Long tenantId,Long machineId);

    // Lọc movement của tenant theo loại.
    List<BottleMovement> findAllByTenantIdAndMovementTypeOrderByCreatedAtDesc(Long tenantId,BottleMovementType movementType);
}
