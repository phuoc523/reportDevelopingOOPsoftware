package com.scentstation.operation.inventory.reconciliation.repository;

import com.scentstation.operation.inventory.reconciliation.entity.SensorReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SensorReconciliationRepository
        extends JpaRepository<SensorReconciliation, Long> {

    Optional<SensorReconciliation> findByTenantIdAndSensorEventId(
            Long tenantId,
            String sensorEventId
    );

    List<SensorReconciliation>
    findAllByTenantIdAndBottleIdOrderByProcessedAtDesc(
            Long tenantId,
            Long bottleId
    );

    List<SensorReconciliation>
    findAllByTenantIdAndMachineIdOrderByProcessedAtDesc(
            Long tenantId,
            Long machineId
    );
}