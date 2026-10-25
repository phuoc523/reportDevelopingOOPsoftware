package com.scentstation.operation.inventory.refill.service;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import com.scentstation.operation.inventory.movement.repository.BottleMovementRepository;
import com.scentstation.operation.inventory.refill.dto.request.RefillBottleRequest;
import com.scentstation.operation.inventory.refill.dto.response.RefillBottleResponse;
import com.scentstation.operation.inventory.refill.exception.BottleNotRefillableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefillService {

    private final BottleRepository bottleRepository;
    private final BottleInstallationRepository bottleInstallationRepository;
    private final BottleMovementRepository bottleMovementRepository;

    @Transactional
    public RefillBottleResponse refillBottle(
            RefillBottleRequest request
    ) {
        Bottle bottle = bottleRepository
                .findByIdAndTenantId(
                        request.getBottleId(),
                        request.getTenantId()
                )
                .orElseThrow(() ->
                        new BottleNotFoundException(
                                request.getBottleId(),
                                request.getTenantId()
                        )
                );

        validateRefillable(bottle);

        BigDecimal quantityBefore =
                bottle.getCurrentQuantity();

        BigDecimal quantityAfter =
                quantityBefore.add(
                        request.getRefillQuantity()
                );

        bottle.setCurrentQuantity(quantityAfter);

        updateStatusAfterRefill(
                bottle,
                request.getTenantId()
        );

        bottleRepository.save(bottle);

        saveMovement(
                request,
                bottle,
                quantityBefore,
                quantityAfter
        );

        return RefillBottleResponse.builder()
                .bottleId(bottle.getId())
                .tenantId(bottle.getTenantId())
                .quantityBefore(quantityBefore)
                .refillQuantity(request.getRefillQuantity())
                .quantityAfter(quantityAfter)
                .status(bottle.getStatus())
                .actorId(request.getActorId())
                .reason(request.getReason())
                .refilledAt(LocalDateTime.now())
                .build();
    }

    private void validateRefillable(
            Bottle bottle
    ) {
        if (bottle.getStatus() == BottleStatus.DAMAGED
                || bottle.getStatus() == BottleStatus.EXPIRED) {

            throw new BottleNotRefillableException(
                    bottle.getId(),
                    bottle.getStatus().name()
            );
        }
    }

    private void updateStatusAfterRefill(
            Bottle bottle,
            Long tenantId
    ) {
        boolean isInstalled =
                bottleInstallationRepository
                        .existsByTenantIdAndBottleIdAndActiveTrue(
                                tenantId,
                                bottle.getId()
                        );

        if (isInstalled) {
            bottle.setStatus(BottleStatus.INSTALLED);
        } else {
            bottle.setStatus(BottleStatus.AVAILABLE);
        }
    }

    private void saveMovement(
            RefillBottleRequest request,
            Bottle bottle,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter
    ) {
        BottleMovement movement =
                BottleMovement.builder()
                        .tenantId(request.getTenantId())
                        .bottleId(bottle.getId())
                        .movementType(BottleMovementType.REFILL)
                        .quantityBefore(quantityBefore)
                        .quantityAfter(quantityAfter)
                        .actorId(request.getActorId())
                        .reason(request.getReason())
                        .build();

        bottleMovementRepository.save(movement);
    }
}