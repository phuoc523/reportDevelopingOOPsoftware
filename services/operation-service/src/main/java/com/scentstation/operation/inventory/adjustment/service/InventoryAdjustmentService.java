package com.scentstation.operation.inventory.adjustment.service;

import com.scentstation.operation.inventory.adjustment.dto.request.AdjustInventoryRequest;
import com.scentstation.operation.inventory.adjustment.dto.response.InventoryAdjustmentResponse;
import com.scentstation.operation.inventory.adjustment.entity.InventoryAdjustment;
import com.scentstation.operation.inventory.adjustment.repository.InventoryAdjustmentRepository;
import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import com.scentstation.operation.inventory.movement.repository.BottleMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final BottleRepository bottleRepository;
    private final BottleInstallationRepository installationRepository;
    private final BottleMovementRepository movementRepository;

    @Transactional
    public InventoryAdjustmentResponse adjust(
            AdjustInventoryRequest request
    ) {
        Bottle bottle = getBottle(
                request.getTenantId(),
                request.getBottleId()
        );

        BigDecimal quantityBefore =
                bottle.getCurrentQuantity();

        BigDecimal quantityAfter =
                request.getNewQuantity();

        bottle.setCurrentQuantity(quantityAfter);

        updateBottleStatus(
                bottle,
                request.getTenantId()
        );

        bottleRepository.save(bottle);

        InventoryAdjustment adjustment =
                InventoryAdjustment.builder()
                        .tenantId(request.getTenantId())
                        .bottleId(request.getBottleId())
                        .quantityBefore(quantityBefore)
                        .quantityAfter(quantityAfter)
                        .actorId(request.getActorId())
                        .reason(request.getReason())
                        .build();

        InventoryAdjustment saved =
                adjustmentRepository.save(adjustment);

        saveMovement(
                request,
                quantityBefore,
                quantityAfter
        );

        return toResponse(
                saved,
                bottle
        );
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getBottleAdjustmentHistory(
            Long tenantId,
            Long bottleId
    ) {
        getBottle(tenantId, bottleId);

        return adjustmentRepository
                .findAllByTenantIdAndBottleIdOrderByCreatedAtDesc(
                        tenantId,
                        bottleId
                )
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private InventoryAdjustmentResponse toHistoryResponse(
            InventoryAdjustment adjustment
    ) {
        BigDecimal difference =
                adjustment.getQuantityAfter()
                        .subtract(
                                adjustment.getQuantityBefore()
                        );

        return InventoryAdjustmentResponse.builder()
                .id(adjustment.getId())
                .tenantId(adjustment.getTenantId())
                .bottleId(adjustment.getBottleId())
                .quantityBefore(
                        adjustment.getQuantityBefore()
                )
                .quantityAfter(
                        adjustment.getQuantityAfter()
                )
                .difference(difference)
                .actorId(adjustment.getActorId())
                .reason(adjustment.getReason())
                .createdAt(adjustment.getCreatedAt())
                .build();
    }

    private Bottle getBottle(
            Long tenantId,
            Long bottleId
    ) {
        return bottleRepository
                .findByIdAndTenantId(
                        bottleId,
                        tenantId
                )
                .orElseThrow(() ->
                        new BottleNotFoundException(
                                bottleId,
                                tenantId
                        )
                );
    }

    private void updateBottleStatus(
            Bottle bottle,
            Long tenantId
    ) {
        /*
         * Adjustment không được tự phục hồi chai
         * đang DAMAGED hoặc EXPIRED.
         */
        if (bottle.getStatus() == BottleStatus.DAMAGED
                || bottle.getStatus() == BottleStatus.EXPIRED) {
            return;
        }

        if (bottle.getCurrentQuantity()
                .compareTo(BigDecimal.ZERO) == 0) {

            bottle.setStatus(BottleStatus.EMPTY);
            return;
        }

        boolean installed =
                installationRepository
                        .existsByTenantIdAndBottleIdAndActiveTrue(
                                tenantId,
                                bottle.getId()
                        );

        if (installed) {
            bottle.setStatus(BottleStatus.INSTALLED);
        } else {
            bottle.setStatus(BottleStatus.AVAILABLE);
        }
    }

    private void saveMovement(
            AdjustInventoryRequest request,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter
    ) {
        Long machineId = null;
        Long slotId = null;

        var activeInstallation =
                installationRepository
                        .findByTenantIdAndBottleIdAndActiveTrue(
                                request.getTenantId(),
                                request.getBottleId()
                        );

        if (activeInstallation.isPresent()) {
            machineId = activeInstallation.get().getMachineId();
            slotId = activeInstallation.get().getSlotId();
        }

        BottleMovement movement =
                BottleMovement.builder()
                        .tenantId(request.getTenantId())
                        .bottleId(request.getBottleId())
                        .movementType(
                                BottleMovementType.ADJUSTMENT
                        )
                        .machineId(machineId)
                        .slotId(slotId)
                        .quantityBefore(quantityBefore)
                        .quantityAfter(quantityAfter)
                        .actorId(request.getActorId())
                        .reason(request.getReason())
                        .build();

        movementRepository.save(movement);
    }

    private InventoryAdjustmentResponse toResponse(
            InventoryAdjustment adjustment,
            Bottle bottle
    ) {
        BigDecimal difference =
                adjustment.getQuantityAfter()
                        .subtract(
                                adjustment.getQuantityBefore()
                        );

        return InventoryAdjustmentResponse.builder()
                .id(adjustment.getId())
                .tenantId(adjustment.getTenantId())
                .bottleId(adjustment.getBottleId())
                .quantityBefore(
                        adjustment.getQuantityBefore()
                )
                .quantityAfter(
                        adjustment.getQuantityAfter()
                )
                .difference(difference)
                .actorId(adjustment.getActorId())
                .reason(adjustment.getReason())
                .bottleStatus(bottle.getStatus())
                .createdAt(adjustment.getCreatedAt())
                .build();
    }
}