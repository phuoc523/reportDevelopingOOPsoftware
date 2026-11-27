package com.scentstation.operation.inventory.consumption.service;

import com.scentstation.operation.inventory.audit.entity.InventoryAuditEvent;
import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.audit.service.InventoryAuditService;
import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.consumption.dto.request.RecordDispenseRequest;
import com.scentstation.operation.inventory.consumption.dto.request.RecordInstalledDispenseRequest;
import com.scentstation.operation.inventory.consumption.dto.response.DispenseConsumptionResponse;
import com.scentstation.operation.inventory.consumption.entity.DispenseConsumption;
import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.exception.DuplicateDispenseCommandException;
import com.scentstation.operation.inventory.consumption.exception.InsufficientBottleQuantityException;
import com.scentstation.operation.inventory.consumption.repository.DispenseConsumptionRepository;
import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import com.scentstation.operation.inventory.installation.exception.ActiveInstallationNotFoundException;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import com.scentstation.operation.inventory.movement.repository.BottleMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.scentstation.operation.inventory.consumption.exception.BottleInstallationMismatchException;
import com.scentstation.operation.inventory.consumption.exception.DispenseConsumptionNotFoundException;
import com.scentstation.operation.inventory.consumption.event.InventoryLowStockEvent;
import com.scentstation.operation.inventory.estimation.exception.SprayCalibrationNotFoundException;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class DispenseConsumptionService {

    private final DispenseConsumptionRepository dispenseConsumptionRepository;
    private final BottleRepository bottleRepository;
    private final BottleInstallationRepository bottleInstallationRepository;
    private final BottleMovementRepository bottleMovementRepository;
    private final InventoryAuditService auditService;
    private final ApplicationEventPublisher events;

    @Transactional
    public DispenseConsumptionResponse recordInstalledDispense(RecordInstalledDispenseRequest request) {
        var existing = dispenseConsumptionRepository.findByTenantIdAndCommandId(
                request.getTenantId(), request.getCommandId());
        if (existing.isPresent()) {
            Bottle existingBottle = getBottle(request.getTenantId(), existing.get().getBottleId());
            return toResponse(existing.get(), existingBottle);
        }

        BottleInstallation installation = bottleInstallationRepository
                .findByTenantIdAndMachineIdAndSlotIdAndActiveTrue(
                        request.getTenantId(), request.getMachineId(), request.getSlotId())
                .orElseThrow(() -> new ActiveInstallationNotFoundException(
                        request.getTenantId(), request.getMachineId(), request.getSlotId()));
        Bottle bottle = getBottle(request.getTenantId(), installation.getBottleId());
        BigDecimal dose = bottle.getCalibratedConsumptionPerSpray();
        if (dose == null || dose.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SprayCalibrationNotFoundException(bottle.getId());
        }

        DispenseConsumptionResponse response = recordDispense(RecordDispenseRequest.builder()
                .tenantId(request.getTenantId())
                .commandId(request.getCommandId())
                .bottleId(bottle.getId())
                .machineId(request.getMachineId())
                .slotId(request.getSlotId())
                .dispenseType(request.getDispenseType())
                .result(request.getResult())
                .consumedQuantity(dose)
                .build());

        if (request.getResult() == DispenseResult.SUCCESS) {
            long remainingSprays = response.getQuantityAfter().divide(dose, 0, java.math.RoundingMode.DOWN).longValue();
            if (remainingSprays <= 5) {
                if (response.getQuantityAfter().compareTo(BigDecimal.ZERO) > 0
                        && bottle.getStatus() == BottleStatus.INSTALLED) {
                    bottle.setStatus(BottleStatus.NEARLY_EMPTY);
                    bottleRepository.save(bottle);
                }
                events.publishEvent(new InventoryLowStockEvent(
                        request.getTenantId(), request.getCommandId(), request.getMachineId(), request.getSlotId(),
                        bottle.getId(), response.getQuantityAfter(), remainingSprays));
            }
        }
        return response;
    }

    // Ghi nhận một lần dispense; chỉ trừ tồn kho khi kết quả thành công.
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

            saveConsumptionMovement(request, quantityBefore, quantityAfter);

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

        auditService.record(
                InventoryAuditEvent.builder()
                        .tenantId(request.getTenantId())
                        .action(InventoryAuditAction.DISPENSE_RECORDED)
                        .entityType("DISPENSE")
                        .entityId(saved.getId())
                        .machineId(request.getMachineId())
                        .slotId(request.getSlotId())
                        .beforeValue(quantityBefore.toPlainString())
                        .afterValue(quantityAfter.toPlainString())
                        .reason(request.getDispenseType().name())
                        .referenceId(request.getCommandId())
                        .build()
        );

        return toResponse(
                saved,
                bottle
        );
    }

    // Ghi movement riêng cho customer và diagnostic sau dispense vật lý thành công.
    private void saveConsumptionMovement(
            RecordDispenseRequest request,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter
    ) {
        BottleMovementType movementType =
                request.getDispenseType()
                        == com.scentstation.operation.inventory.consumption.enums.DispenseType.CUSTOMER
                        ? BottleMovementType.CUSTOMER_CONSUMPTION
                        : BottleMovementType.DIAGNOSTIC_CONSUMPTION;

        bottleMovementRepository.save(
                BottleMovement.builder()
                        .tenantId(request.getTenantId())
                        .bottleId(request.getBottleId())
                        .movementType(movementType)
                        .machineId(request.getMachineId())
                        .slotId(request.getSlotId())
                        .quantityBefore(quantityBefore)
                        .quantityAfter(quantityAfter)
                        .reason("Confirmed physical dispense")
                        .referenceId(request.getCommandId())
                        .build()
        );
    }

    // Tra cứu kết quả dispense bằng command id trong phạm vi tenant.
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

    // Ngăn xử lý lặp lại cùng một command dispense.
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

    // Lấy chai thuộc tenant hoặc báo lỗi nếu không tồn tại.
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

    // Lấy bản ghi lắp đặt đang hoạt động của chai.
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

    // Bảo đảm machine và slot trong yêu cầu khớp vị trí lắp đặt thực tế.
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

    // Bảo đảm chai còn đủ lượng để thực hiện dispense.
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

    // Chuyển chai sang EMPTY khi lượng còn lại bằng không.
    private void updateBottleStatusAfterConsumption(
            Bottle bottle,
            BigDecimal quantityAfter
    ) {
        if (quantityAfter.compareTo(BigDecimal.ZERO) == 0) {
            bottle.setStatus(BottleStatus.EMPTY);
        }
    }

    // Chuyển bản ghi tiêu thụ và trạng thái chai thành dữ liệu trả về API.
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
