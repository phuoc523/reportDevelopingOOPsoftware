package com.scentstation.operation.inventory.estimation.service;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.repository.DispenseConsumptionRepository;
import com.scentstation.operation.inventory.estimation.dto.request.UpdateSprayCalibrationRequest;
import com.scentstation.operation.inventory.estimation.dto.response.RemainingSprayResponse;
import com.scentstation.operation.inventory.estimation.exception.SprayCalibrationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemainingSprayServiceTest {

    @Mock
    private BottleRepository bottleRepository;

    @Mock
    private DispenseConsumptionRepository dispenseConsumptionRepository;

    @InjectMocks
    private RemainingSprayService remainingSprayService;

    private Bottle bottle;

    @BeforeEach
    void setUp() {
        bottle = Bottle.builder()
                .id(10L)
                .tenantId(1L)
                .productId(100L)
                .batchId(5L)
                .bottleCode("BTL-001")
                .initialQuantity(new BigDecimal("100.000"))
                .currentQuantity(new BigDecimal("19.800"))
                .calibratedConsumptionPerSpray(
                        new BigDecimal("0.200")
                )
                .status(BottleStatus.INSTALLED)
                .build();
    }

    @Test
    void shouldCalculateRemainingSpraysSuccessfully() {
        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(dispenseConsumptionRepository
                .countByTenantIdAndBottleIdAndResult(
                        1L,
                        10L,
                        DispenseResult.SUCCESS
                ))
                .thenReturn(245L);

        RemainingSprayResponse result =
                remainingSprayService.getRemainingSprays(
                        1L,
                        10L
                );

        assertEquals(99L, result.getEstimatedRemainingSprays());
        assertEquals(245L, result.getSuccessfulSprayCount());

        assertEquals(
                0,
                new BigDecimal("19.800")
                        .compareTo(result.getCurrentQuantity())
        );

        assertEquals(
                0,
                new BigDecimal("0.200")
                        .compareTo(
                                result.getCalibratedConsumptionPerSpray()
                        )
        );
    }

    @Test
    void shouldRoundRemainingSpraysDown() {
        bottle.setCurrentQuantity(
                new BigDecimal("1.100")
        );

        bottle.setCalibratedConsumptionPerSpray(
                new BigDecimal("0.200")
        );

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(dispenseConsumptionRepository
                .countByTenantIdAndBottleIdAndResult(
                        1L,
                        10L,
                        DispenseResult.SUCCESS
                ))
                .thenReturn(5L);

        RemainingSprayResponse result =
                remainingSprayService.getRemainingSprays(
                        1L,
                        10L
                );

        assertEquals(
                5L,
                result.getEstimatedRemainingSprays()
        );
    }

    @Test
    void shouldUpdateCalibrationSuccessfully() {
        bottle.setCalibratedConsumptionPerSpray(null);

        UpdateSprayCalibrationRequest request =
                UpdateSprayCalibrationRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .consumptionPerSpray(
                                new BigDecimal("0.250")
                        )
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(dispenseConsumptionRepository
                .countByTenantIdAndBottleIdAndResult(
                        1L,
                        10L,
                        DispenseResult.SUCCESS
                ))
                .thenReturn(100L);

        RemainingSprayResponse result =
                remainingSprayService.updateCalibration(request);

        assertEquals(
                0,
                new BigDecimal("0.250")
                        .compareTo(
                                bottle.getCalibratedConsumptionPerSpray()
                        )
        );

        assertEquals(
                79L,
                result.getEstimatedRemainingSprays()
        );

        verify(bottleRepository).save(bottle);
    }

    @Test
    void shouldRejectWhenCalibrationDoesNotExist() {
        bottle.setCalibratedConsumptionPerSpray(null);

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        assertThrows(
                SprayCalibrationNotFoundException.class,
                () -> remainingSprayService
                        .getRemainingSprays(1L, 10L)
        );
    }

    @Test
    void shouldRejectWhenBottleDoesNotExistForTenant() {
        when(bottleRepository.findByIdAndTenantId(
                999L,
                1L
        )).thenReturn(Optional.empty());

        assertThrows(
                BottleNotFoundException.class,
                () -> remainingSprayService
                        .getRemainingSprays(1L, 999L)
        );
    }

    @Test
    void shouldReturnZeroWhenBottleIsEmpty() {
        bottle.setCurrentQuantity(BigDecimal.ZERO);

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(dispenseConsumptionRepository
                .countByTenantIdAndBottleIdAndResult(
                        1L,
                        10L,
                        DispenseResult.SUCCESS
                ))
                .thenReturn(500L);

        RemainingSprayResponse result =
                remainingSprayService.getRemainingSprays(
                        1L,
                        10L
                );

        assertEquals(
                0L,
                result.getEstimatedRemainingSprays()
        );
    }
}