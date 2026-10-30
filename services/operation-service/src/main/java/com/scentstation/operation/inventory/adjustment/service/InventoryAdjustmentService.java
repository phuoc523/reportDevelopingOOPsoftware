package com.scentstation.operation.inventory.adjustment.service;

import com.scentstation.operation.inventory.adjustment.dto.request.AdjustInventoryRequest;
import com.scentstation.operation.inventory.adjustment.dto.response.InventoryAdjustmentResponse;
import com.scentstation.operation.inventory.adjustment.entity.InventoryAdjustment;
import com.scentstation.operation.inventory.adjustment.repository.InventoryAdjustmentRepository;
import com.scentstation.operation.inventory.audit.entity.InventoryAuditEvent;
import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.audit.service.InventoryAuditService;
import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import com.scentstation.operation.inventory.movement.repository.BottleMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final BottleRepository bottleRepository;
    private final BottleInstallationRepository installationRepository;
    private final BottleMovementRepository movementRepository;
    private final InventoryAuditService auditService;

    // Điều chỉnh tồn kho và ghi movement, audit từ cùng snapshot installation.
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

        Optional<BottleInstallation> activeInstallation =
                installationRepository
                        .findByTenantIdAndBottleIdAndActiveTrue(
                                request.getTenantId(),
                                request.getBottleId()
                        );

        bottle.setCurrentQuantity(quantityAfter);

        updateBottleStatus(
                bottle,
                activeInstallation.isPresent()
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
                quantityAfter,
                activeInstallation
        );

        saveAudit(
                request,
                quantityBefore,
                quantityAfter,
                activeInstallation
        );

        return toResponse(
                saved,
                bottle
        );
    }

    // Trả lịch sử điều chỉnh của bottle sau khi xác minh tenant sở hữu bottle.
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

    // Chuyển adjustment lịch sử thành response và tính độ chênh lệch.
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

    // Lấy bottle đúng tenant hoặc báo lỗi nếu không tồn tại.
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

    // Đồng bộ trạng thái bottle theo lượng mới và snapshot installation.
    private void updateBottleStatus(
            Bottle bottle,
            boolean installed
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

        if (installed) {
            bottle.setStatus(BottleStatus.INSTALLED);
        } else {
            bottle.setStatus(BottleStatus.AVAILABLE);
        }
    }

    // Ghi biến động vật lý của bottle từ snapshot trước và sau adjustment.
    private void saveMovement(
            AdjustInventoryRequest request,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            Optional<BottleInstallation> activeInstallation
    ) {
        Long machineId = activeInstallation
                .map(BottleInstallation::getMachineId)
                .orElse(null);
        Long slotId = activeInstallation
                .map(BottleInstallation::getSlotId)
                .orElse(null);

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

    // Ghi audit nghiệp vụ adjustment sau khi các mutation chính đã lưu thành công.
    private void saveAudit(
            AdjustInventoryRequest request,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            Optional<BottleInstallation> activeInstallation
    ) {
        auditService.record(
                InventoryAuditEvent.builder()
                        .tenantId(request.getTenantId())
                        .actorId(request.getActorId())
                        .action(InventoryAuditAction.INVENTORY_ADJUSTMENT)
                        .entityType("BOTTLE")
                        .entityId(request.getBottleId())
                        .machineId(activeInstallation
                                .map(BottleInstallation::getMachineId)
                                .orElse(null))
                        .slotId(activeInstallation
                                .map(BottleInstallation::getSlotId)
                                .orElse(null))
                        .beforeValue(quantityBefore.toPlainString())
                        .afterValue(quantityAfter.toPlainString())
                        .reason(request.getReason())
                        .build()
        );
    }

    // Chuyển adjustment mới lưu thành response kèm trạng thái bottle hiện tại.
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
