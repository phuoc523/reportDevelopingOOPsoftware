package com.scentstation.operation.inventory.consumption.service;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.consumption.dto.request.RecordDispenseRequest;
import com.scentstation.operation.inventory.consumption.dto.response.DispenseConsumptionResponse;
import com.scentstation.operation.inventory.consumption.entity.DispenseConsumption;
import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.exception.DuplicateDispenseCommandException;
import com.scentstation.operation.inventory.consumption.exception.InsufficientBottleQuantityException;
import com.scentstation.operation.inventory.consumption.repository.DispenseConsumptionRepository;
import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import com.scentstation.operation.inventory.installation.exception.ActiveInstallationNotFoundException;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.scentstation.operation.inventory.consumption.exception.BottleInstallationMismatchException;
import com.scentstation.operation.inventory.consumption.exception.DispenseConsumptionNotFoundException;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class DispenseConsumptionService {

    private final DispenseConsumptionRepository dispenseConsumptionRepository;
    private final BottleRepository bottleRepository;
    private final BottleInstallationRepository bottleInstallationRepository;

    @Transactional
    public DispenseConsumptionResponse recordDispense(
            RecordDispenseRequest request
    ) {
        validateCommandNotProcessed(request);

        Bottle bottle = getBottle(
                request.getTenantId(),
                request.getBottleId()
        );

        BottleInstallation installation =
                getActiveInstallation(
                        request.getTenantId(),
                        request.getBottleId()
                );

        validateInstallationMatchesRequest(
                request,
                installation
        );

        BigDecimal quantityBefore =
                bottle.getCurrentQuantity();

        BigDecimal quantityAfter =
                quantityBefore;

        boolean inventoryDeducted = false;

        if (request.getResult() == DispenseResult.SUCCESS) {

            validateSufficientQuantity(
                    bottle,
                    request.getConsumedQuantity()
            );

            quantityAfter =
                    quantityBefore.subtract(
                            request.getConsumedQuantity()
                    );

            bottle.setCurrentQuantity(quantityAfter);

            updateBottleStatusAfterConsumption(
                    bottle,
                    quantityAfter
            );

            bottleRepository.save(bottle);

            inventoryDeducted = true;
        }

        DispenseConsumption consumption =
                DispenseConsumption.builder()
                        .tenantId(request.getTenantId())
                        .commandId(request.getCommandId())
                        .bottleId(request.getBottleId())
                        .machineId(request.getMachineId())
                        .slotId(request.getSlotId())
                        .dispenseType(request.getDispenseType())
                        .result(request.getResult())
                        .consumedQuantity(request.getConsumedQuantity())
                        .quantityBefore(quantityBefore)
                        .quantityAfter(quantityAfter)
                        .inventoryDeducted(inventoryDeducted)
                        .build();

        DispenseConsumption saved =
                dispenseConsumptionRepository.save(consumption);

        return toResponse(
                saved,
                bottle
        );
    }

    @Transactional(readOnly = true)
    public DispenseConsumptionResponse getByCommandId(
            Long tenantId,
            String commandId
    ) {
        DispenseConsumption consumption =
                dispenseConsumptionRepository
                        .findByTenantIdAndCommandId(
                                tenantId,
                                commandId
                        )
                        .orElseThrow(() ->
                                new DispenseConsumptionNotFoundException(
                                        tenantId,
                                        commandId
                                )
                        );

        Bottle bottle = getBottle(
                tenantId,
                consumption.getBottleId()
        );

        return toResponse(
                consumption,
                bottle
        );
    }

    private void validateCommandNotProcessed(
            RecordDispenseRequest request
    ) {
        boolean exists =
                dispenseConsumptionRepository
                        .existsByTenantIdAndCommandId(
                                request.getTenantId(),
                                request.getCommandId()
                        );

        if (exists) {
            throw new DuplicateDispenseCommandException(
                    request.getTenantId(),
                    request.getCommandId()
            );
        }
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

    private BottleInstallation getActiveInstallation(
            Long tenantId,
            Long bottleId
    ) {
        return bottleInstallationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(
                        tenantId,
                        bottleId
                )
                .orElseThrow(() ->
                        new ActiveInstallationNotFoundException(
                                bottleId,
                                tenantId
                        )
                );
    }

    private void validateInstallationMatchesRequest(
            RecordDispenseRequest request,
            BottleInstallation installation
    ) {
        boolean machineMatches =
                installation.getMachineId()
                        .equals(request.getMachineId());

        boolean slotMatches =
                installation.getSlotId()
                        .equals(request.getSlotId());

        if (!machineMatches || !slotMatches) {
            throw new BottleInstallationMismatchException(
                    installation.getBottleId(),
                    request.getMachineId(),
                    request.getSlotId()
            );
        }
    }

    private void validateSufficientQuantity(
            Bottle bottle,
            BigDecimal consumedQuantity
    ) {
        if (bottle.getCurrentQuantity()
                .compareTo(consumedQuantity) < 0) {

            throw new InsufficientBottleQuantityException(
                    bottle.getId(),
                    bottle.getCurrentQuantity(),
                    consumedQuantity
            );
        }
    }

    private void updateBottleStatusAfterConsumption(
            Bottle bottle,
            BigDecimal quantityAfter
    ) {
        if (quantityAfter.compareTo(BigDecimal.ZERO) == 0) {
            bottle.setStatus(BottleStatus.EMPTY);
        }
    }

    private DispenseConsumptionResponse toResponse(
            DispenseConsumption consumption,
            Bottle bottle
    ) {
        return DispenseConsumptionResponse.builder()
                .id(consumption.getId())
                .commandId(consumption.getCommandId())
                .bottleId(consumption.getBottleId())
                .machineId(consumption.getMachineId())
                .slotId(consumption.getSlotId())
                .dispenseType(consumption.getDispenseType())
                .result(consumption.getResult())
                .consumedQuantity(consumption.getConsumedQuantity())
                .quantityBefore(consumption.getQuantityBefore())
                .quantityAfter(consumption.getQuantityAfter())
                .inventoryDeducted(consumption.getInventoryDeducted())
                .bottleStatus(bottle.getStatus())
                .processedAt(consumption.getProcessedAt())
                .build();
    }
}