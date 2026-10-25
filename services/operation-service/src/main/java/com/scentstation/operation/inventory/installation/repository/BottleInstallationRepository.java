package com.scentstation.operation.inventory.installation.repository;

import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BottleInstallationRepository
        extends JpaRepository<BottleInstallation, Long> {

    Optional<BottleInstallation>
    findByTenantIdAndMachineIdAndSlotIdAndActiveTrue(
            Long tenantId,
            Long machineId,
            Long slotId
    );

    Optional<BottleInstallation>
    findByTenantIdAndBottleIdAndActiveTrue(
            Long tenantId,
            Long bottleId
    );

    boolean existsByTenantIdAndMachineIdAndSlotIdAndActiveTrue(
            Long tenantId,
            Long machineId,
            Long slotId
    );

    boolean existsByTenantIdAndBottleIdAndActiveTrue(
            Long tenantId,
            Long bottleId
    );

    List<BottleInstallation>
    findAllByTenantIdAndBottleIdOrderByInstalledAtDesc(
            Long tenantId,
            Long bottleId
    );

    List<BottleInstallation>
    findAllByTenantIdAndMachineIdOrderByInstalledAtDesc(
            Long tenantId,
            Long machineId
    );
}