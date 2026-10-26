package com.scentstation.operation.inventory.estimation.service;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.repository.DispenseConsumptionRepository;
import com.scentstation.operation.inventory.estimation.dto.request.UpdateSprayCalibrationRequest;
import com.scentstation.operation.inventory.estimation.dto.response.RemainingSprayResponse;
import com.scentstation.operation.inventory.estimation.exception.SprayCalibrationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class RemainingSprayService {

    private final BottleRepository bottleRepository;
    private final DispenseConsumptionRepository dispenseConsumptionRepository;

    @Transactional
    public RemainingSprayResponse updateCalibration(
            UpdateSprayCalibrationRequest request
    ) {
        Bottle bottle = getBottle(
                request.getTenantId(),
                request.getBottleId()
        );

        bottle.setCalibratedConsumptionPerSpray(
                request.getConsumptionPerSpray()
        );

        bottleRepository.save(bottle);

        return buildResponse(bottle);
    }

    @Transactional(readOnly = true)
    public RemainingSprayResponse getRemainingSprays(
            Long tenantId,
            Long bottleId
    ) {
        Bottle bottle = getBottle(
                tenantId,
                bottleId
        );

        validateCalibration(bottle);

        return buildResponse(bottle);
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

    private void validateCalibration(
            Bottle bottle
    ) {
        if (bottle.getCalibratedConsumptionPerSpray() == null
                || bottle.getCalibratedConsumptionPerSpray()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new SprayCalibrationNotFoundException(
                    bottle.getId()
            );
        }
    }

    private RemainingSprayResponse buildResponse(
            Bottle bottle
    ) {
        validateCalibration(bottle);

        long estimatedRemainingSprays =
                calculateRemainingSprays(bottle);

        long successfulSprayCount =
                dispenseConsumptionRepository
                        .countByTenantIdAndBottleIdAndResult(
                                bottle.getTenantId(),
                                bottle.getId(),
                                DispenseResult.SUCCESS
                        );

        return RemainingSprayResponse.builder()
                .bottleId(bottle.getId())
                .tenantId(bottle.getTenantId())
                .currentQuantity(bottle.getCurrentQuantity())
                .calibratedConsumptionPerSpray(
                        bottle.getCalibratedConsumptionPerSpray()
                )
                .estimatedRemainingSprays(
                        estimatedRemainingSprays
                )
                .successfulSprayCount(
                        successfulSprayCount
                )
                .bottleStatus(bottle.getStatus())
                .build();
    }

    private long calculateRemainingSprays(
            Bottle bottle
    ) {
        BigDecimal result =
                bottle.getCurrentQuantity()
                        .divide(
                                bottle.getCalibratedConsumptionPerSpray(),
                                0,
                                RoundingMode.DOWN
                        );

        return result.longValue();
    }
}