package com.scentstation.operation.inventory.consumption.repository;

import com.scentstation.operation.inventory.consumption.entity.DispenseConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DispenseConsumptionRepository
        extends JpaRepository<DispenseConsumption, Long> {

    boolean existsByTenantIdAndCommandId(
            Long tenantId,
            String commandId
    );

    Optional<DispenseConsumption> findByTenantIdAndCommandId(
            Long tenantId,
            String commandId
    );

    List<DispenseConsumption>
    findAllByTenantIdAndBottleIdOrderByProcessedAtDesc(
            Long tenantId,
            Long bottleId
    );

    List<DispenseConsumption>
    findAllByTenantIdAndMachineIdOrderByProcessedAtDesc(
            Long tenantId,
            Long machineId
    );
}