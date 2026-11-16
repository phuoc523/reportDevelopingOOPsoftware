package com.scentstation.operation.inventory.consumption.repository;

import com.scentstation.operation.inventory.consumption.entity.DispenseConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.scentstation.operation.inventory.consumption.enums.DispenseResult;

import java.util.List;
import java.util.Optional;

@Repository
public interface DispenseConsumptionRepository
        extends JpaRepository<DispenseConsumption, Long> {

    // Kiểm tra command id đã được xử lý trong tenant hay chưa.
    boolean existsByTenantIdAndCommandId(
            Long tenantId,
            String commandId
    );

    // Tìm kết quả dispense theo tenant và command id.
    Optional<DispenseConsumption> findByTenantIdAndCommandId(
            Long tenantId,
            String commandId
    );

    // Trả lịch sử dispense của một bottle.
    List<DispenseConsumption>
    findAllByTenantIdAndBottleIdOrderByProcessedAtDesc(
            Long tenantId,
            Long bottleId
    );

    // Trả lịch sử dispense của một machine.
    List<DispenseConsumption>
    findAllByTenantIdAndMachineIdOrderByProcessedAtDesc(
            Long tenantId,
            Long machineId
    );

    // Đếm dispense của bottle theo kết quả xử lý.
    long countByTenantIdAndBottleIdAndResult(
                Long tenantId,
                Long bottleId,
                DispenseResult result
        );
}
