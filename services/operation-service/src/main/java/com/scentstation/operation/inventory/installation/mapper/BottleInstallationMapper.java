package com.scentstation.operation.inventory.installation.mapper;

import com.scentstation.operation.inventory.installation.dto.request.InstallBottleRequest;
import com.scentstation.operation.inventory.installation.dto.response.BottleInstallationResponse;
import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import org.springframework.stereotype.Component;

@Component
public class BottleInstallationMapper {

    // Chuyển install request thành active installation entity.
    public BottleInstallation toEntity(
            InstallBottleRequest request
    ) {
        return BottleInstallation.builder()
                .tenantId(request.getTenantId())
                .bottleId(request.getBottleId())
                .machineId(request.getMachineId())
                .slotId(request.getSlotId())
                .installedBy(request.getActorId())
                .active(true)
                .build();
    }

    // Chuyển installation entity thành response API.
    public BottleInstallationResponse toResponse(
            BottleInstallation installation
    ) {
        return BottleInstallationResponse.builder()
                .id(installation.getId())
                .tenantId(installation.getTenantId())
                .bottleId(installation.getBottleId())
                .machineId(installation.getMachineId())
                .slotId(installation.getSlotId())
                .installedBy(installation.getInstalledBy())
                .installedAt(installation.getInstalledAt())
                .removedBy(installation.getRemovedBy())
                .removedAt(installation.getRemovedAt())
                .active(installation.getActive())
                .build();
    }
}
