package com.scentstation.operation.inventory.reconciliation.repository;

import com.scentstation.operation.inventory.reconciliation.entity.SensorReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SensorReconciliationRepository
        extends JpaRepository<SensorReconciliation, Long> {

    // Tìm reconciliation theo idempotency key của sensor.
    Optional<SensorReconciliation> findByTenantIdAndSensorEventId(
            Long tenantId,
            String sensorEventId
    );

    // Trả lịch sử reconciliation của bottle.
    List<SensorReconciliation>
    findAllByTenantIdAndBottleIdOrderByProcessedAtDesc(
            Long tenantId,
            Long bottleId
    );

    // Trả lịch sử reconciliation của machine.
    List<SensorReconciliation>
    findAllByTenantIdAndMachineIdOrderByProcessedAtDesc(
            Long tenantId,
            Long machineId
    );
}
