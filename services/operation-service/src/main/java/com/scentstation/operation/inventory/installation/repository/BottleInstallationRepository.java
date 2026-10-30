package com.scentstation.operation.inventory.installation.repository;

import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BottleInstallationRepository
        extends JpaRepository<BottleInstallation, Long> {

    // Tìm installation active tại một machine slot.
    Optional<BottleInstallation>
    findByTenantIdAndMachineIdAndSlotIdAndActiveTrue(
            Long tenantId,
            Long machineId,
            Long slotId
    );

    // Tìm installation active của một bottle.
    Optional<BottleInstallation>
    findByTenantIdAndBottleIdAndActiveTrue(
            Long tenantId,
            Long bottleId
    );

    // Kiểm tra machine slot đang bị chiếm trong tenant.
    boolean existsByTenantIdAndMachineIdAndSlotIdAndActiveTrue(
            Long tenantId,
            Long machineId,
            Long slotId
    );

    // Kiểm tra bottle đang có installation active hay không.
    boolean existsByTenantIdAndBottleIdAndActiveTrue(
            Long tenantId,
            Long bottleId
    );

    // Trả lịch sử installation của bottle.
    List<BottleInstallation>
    findAllByTenantIdAndBottleIdOrderByInstalledAtDesc(
            Long tenantId,
            Long bottleId
    );

    // Trả các installation tại machine slot.
    List<BottleInstallation>
    findAllByTenantIdAndMachineIdOrderByInstalledAtDesc(
            Long tenantId,
            Long machineId
    );
}
