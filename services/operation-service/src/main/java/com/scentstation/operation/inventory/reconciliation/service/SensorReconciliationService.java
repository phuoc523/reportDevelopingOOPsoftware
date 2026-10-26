package com.scentstation.operation.inventory.reconciliation.service;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import com.scentstation.operation.inventory.installation.exception.ActiveInstallationNotFoundException;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import com.scentstation.operation.inventory.reconciliation.dto.request.RecordSensorReadingRequest;
import com.scentstation.operation.inventory.reconciliation.dto.response.SensorReconciliationResponse;
import com.scentstation.operation.inventory.reconciliation.entity.SensorReconciliation;
import com.scentstation.operation.inventory.reconciliation.enums.ReconciliationStatus;
import com.scentstation.operation.inventory.reconciliation.exception.SensorInstallationMismatchException;
import com.scentstation.operation.inventory.reconciliation.repository.SensorReconciliationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SensorReconciliationService {

    /*
     * Business policy tạm thời.
     *
     * Đặc tả yêu cầu phát hiện discrepancy / abnormal consumption /
     * low stock nhưng chưa quy định ngưỡng số cụ thể.
     *
     * Sau này có thể đưa các giá trị này sang application.yml.
     */
    private static final BigDecimal NORMAL_TOLERANCE_PERCENT =
            new BigDecimal("0.10");

    private static final BigDecimal POSSIBLE_LEAK_PERCENT =
            new BigDecimal("0.25");

    private static final long LOW_STOCK_REMAINING_SPRAYS = 10L;

    private final SensorReconciliationRepository reconciliationRepository;
    private final BottleRepository bottleRepository;
    private final BottleInstallationRepository installationRepository;

    @Transactional
    public SensorReconciliationResponse reconcile(
            RecordSensorReadingRequest request
    ) {
        Optional<SensorReconciliation> existing =
                reconciliationRepository
                        .findByTenantIdAndSensorEventId(
                                request.getTenantId(),
                                request.getSensorEventId()
                        );

        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Bottle bottle = getBottle(
                request.getTenantId(),
                request.getBottleId()
        );

        BottleInstallation installation =
                getActiveInstallation(
                        request.getTenantId(),
                        request.getBottleId()
                );

        validateInstallation(
                request,
                installation
        );

        BigDecimal predictedQuantity =
                bottle.getCurrentQuantity();

        BigDecimal sensorQuantity =
                request.getSensorQuantity();

        BigDecimal discrepancy =
                predictedQuantity
                        .subtract(sensorQuantity)
                        .abs();

        ReconciliationStatus status =
                determineStatus(
                        predictedQuantity,
                        sensorQuantity,
                        discrepancy
                );

        Long estimatedRemainingSprays =
                calculateEstimatedRemainingSprays(
                        sensorQuantity,
                        bottle.getCalibratedConsumptionPerSpray()
                );

        boolean lowStock =
                estimatedRemainingSprays != null
                        && estimatedRemainingSprays
                        <= LOW_STOCK_REMAINING_SPRAYS;

        SensorReconciliation reconciliation =
                SensorReconciliation.builder()
                        .tenantId(request.getTenantId())
                        .sensorEventId(request.getSensorEventId())
                        .bottleId(request.getBottleId())
                        .machineId(request.getMachineId())
                        .slotId(request.getSlotId())
                        .predictedQuantity(predictedQuantity)
                        .sensorQuantity(sensorQuantity)
                        .discrepancy(discrepancy)
                        .status(status)
                        .lowStock(lowStock)
                        .estimatedRemainingSprays(
                                estimatedRemainingSprays
                        )
                        .measuredAt(request.getMeasuredAt())
                        .build();

        SensorReconciliation saved =
                reconciliationRepository.save(reconciliation);

        return toResponse(saved);
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
        return installationRepository
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

    private void validateInstallation(
            RecordSensorReadingRequest request,
            BottleInstallation installation
    ) {
        boolean machineMatches =
                installation.getMachineId()
                        .equals(request.getMachineId());

        boolean slotMatches =
                installation.getSlotId()
                        .equals(request.getSlotId());

        if (!machineMatches || !slotMatches) {
            throw new SensorInstallationMismatchException(
                    request.getBottleId(),
                    request.getMachineId(),
                    request.getSlotId()
            );
        }
    }

    private ReconciliationStatus determineStatus(
            BigDecimal predictedQuantity,
            BigDecimal sensorQuantity,
            BigDecimal discrepancy
    ) {
        if (predictedQuantity.compareTo(BigDecimal.ZERO) == 0) {

            if (sensorQuantity.compareTo(BigDecimal.ZERO) == 0) {
                return ReconciliationStatus.NORMAL;
            }

            return ReconciliationStatus.DISCREPANCY;
        }

        BigDecimal discrepancyPercent =
                discrepancy.divide(
                        predictedQuantity,
                        4,
                        RoundingMode.HALF_UP
                );

        /*
         * POSSIBLE_LEAK chỉ xảy ra khi sensor thực tế
         * thấp hơn inventory dự đoán đáng kể.
         */
        boolean sensorLowerThanPredicted =
                sensorQuantity.compareTo(predictedQuantity) < 0;

        if (sensorLowerThanPredicted
                && discrepancyPercent
                .compareTo(POSSIBLE_LEAK_PERCENT) >= 0) {

            return ReconciliationStatus.POSSIBLE_LEAK;
        }

        if (discrepancyPercent
                .compareTo(NORMAL_TOLERANCE_PERCENT) > 0) {

            return ReconciliationStatus.DISCREPANCY;
        }

        return ReconciliationStatus.NORMAL;
    }

    private Long calculateEstimatedRemainingSprays(
            BigDecimal sensorQuantity,
            BigDecimal consumptionPerSpray
    ) {
        if (consumptionPerSpray == null
                || consumptionPerSpray
                .compareTo(BigDecimal.ZERO) <= 0) {

            return null;
        }

        return sensorQuantity
                .divide(
                        consumptionPerSpray,
                        0,
                        RoundingMode.DOWN
                )
                .longValue();
    }

    private SensorReconciliationResponse toResponse(
            SensorReconciliation reconciliation
    ) {
        return SensorReconciliationResponse.builder()
                .id(reconciliation.getId())
                .sensorEventId(
                        reconciliation.getSensorEventId()
                )
                .bottleId(reconciliation.getBottleId())
                .machineId(reconciliation.getMachineId())
                .slotId(reconciliation.getSlotId())
                .predictedQuantity(
                        reconciliation.getPredictedQuantity()
                )
                .sensorQuantity(
                        reconciliation.getSensorQuantity()
                )
                .discrepancy(
                        reconciliation.getDiscrepancy()
                )
                .status(reconciliation.getStatus())
                .lowStock(reconciliation.getLowStock())
                .estimatedRemainingSprays(
                        reconciliation.getEstimatedRemainingSprays()
                )
                .measuredAt(reconciliation.getMeasuredAt())
                .processedAt(reconciliation.getProcessedAt())
                .build();
    }
}