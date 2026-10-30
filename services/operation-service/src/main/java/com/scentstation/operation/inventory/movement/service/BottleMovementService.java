package com.scentstation.operation.inventory.movement.service;

import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.movement.dto.response.BottleMovementResponse;
import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import com.scentstation.operation.inventory.movement.repository.BottleMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BottleMovementService {

    private final BottleMovementRepository movementRepository;
    private final BottleRepository bottleRepository;

    // Trả lịch sử movement của bottle sau khi xác minh tenant sở hữu bottle.
    @Transactional(readOnly = true)
    public List<BottleMovementResponse> getBottleHistory(
            Long tenantId,
            Long bottleId
    ) {
        validateBottleExists(
                tenantId,
                bottleId
        );

        return movementRepository
                .findAllByTenantIdAndBottleIdOrderByCreatedAtDesc(
                        tenantId,
                        bottleId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Trả lịch sử movement của một machine trong tenant.
    @Transactional(readOnly = true)
    public List<BottleMovementResponse> getMachineHistory(
            Long tenantId,
            Long machineId
    ) {
        return movementRepository
                .findAllByTenantIdAndMachineIdOrderByCreatedAtDesc(
                        tenantId,
                        machineId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Trả toàn bộ movement của tenant theo thời gian giảm dần.
    @Transactional(readOnly = true)
    public List<BottleMovementResponse> getTenantHistory(
            Long tenantId
    ) {
        return movementRepository
                .findAllByTenantIdOrderByCreatedAtDesc(
                        tenantId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Lọc movement của tenant theo loại nghiệp vụ.
    @Transactional(readOnly = true)
    public List<BottleMovementResponse> getHistoryByType(
            Long tenantId,
            BottleMovementType movementType
    ) {
        return movementRepository
                .findAllByTenantIdAndMovementTypeOrderByCreatedAtDesc(
                        tenantId,
                        movementType
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Bảo đảm bottle tồn tại trong đúng tenant trước khi đọc lịch sử.
    private void validateBottleExists(
            Long tenantId,
            Long bottleId
    ) {
        boolean exists =
                bottleRepository
                        .findByIdAndTenantId(
                                bottleId,
                                tenantId
                        )
                        .isPresent();

        if (!exists) {
            throw new BottleNotFoundException(
                    bottleId,
                    tenantId
            );
        }
    }

    // Chuyển movement thành response và tính chênh lệch quantity.
    private BottleMovementResponse toResponse(
            BottleMovement movement
    ) {
        BigDecimal difference = null;

        if (movement.getQuantityBefore() != null
                && movement.getQuantityAfter() != null) {

            difference =
                    movement.getQuantityAfter()
                            .subtract(
                                    movement.getQuantityBefore()
                            );
        }

        return BottleMovementResponse.builder()
                .id(movement.getId())
                .tenantId(movement.getTenantId())
                .bottleId(movement.getBottleId())
                .movementType(
                        movement.getMovementType()
                )
                .machineId(movement.getMachineId())
                .slotId(movement.getSlotId())
                .quantityBefore(
                        movement.getQuantityBefore()
                )
                .quantityAfter(
                        movement.getQuantityAfter()
                )
                .difference(difference)
                .actorId(movement.getActorId())
                .reason(movement.getReason())
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
