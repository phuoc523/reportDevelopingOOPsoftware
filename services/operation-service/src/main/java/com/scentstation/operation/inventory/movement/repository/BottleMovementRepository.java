package com.scentstation.operation.inventory.movement.repository;

import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository 
public interface BottleMovementRepository extends JpaRepository<BottleMovement, Long> {
    List<BottleMovement> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<BottleMovement> findAllByTenantIdAndBottleIdOrderByCreatedAtDesc(Long tenantId,Long bottleId);

    List<BottleMovement> findAllByTenantIdAndMachineIdOrderByCreatedAtDesc(Long tenantId,Long machineId);

    List<BottleMovement> findAllByTenantIdAndMovementTypeOrderByCreatedAtDesc(Long tenantId,BottleMovementType movementType);
}
