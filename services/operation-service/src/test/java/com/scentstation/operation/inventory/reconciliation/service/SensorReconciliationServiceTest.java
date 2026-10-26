package com.scentstation.operation.inventory.reconciliation.service;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import com.scentstation.operation.inventory.reconciliation.dto.request.RecordSensorReadingRequest;
import com.scentstation.operation.inventory.reconciliation.dto.response.SensorReconciliationResponse;
import com.scentstation.operation.inventory.reconciliation.entity.SensorReconciliation;
import com.scentstation.operation.inventory.reconciliation.enums.ReconciliationStatus;
import com.scentstation.operation.inventory.reconciliation.exception.SensorInstallationMismatchException;
import com.scentstation.operation.inventory.reconciliation.repository.SensorReconciliationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorReconciliationServiceTest {

    @Mock
    private SensorReconciliationRepository reconciliationRepository;

    @Mock
    private BottleRepository bottleRepository;

    @Mock
    private BottleInstallationRepository installationRepository;

    @InjectMocks
    private SensorReconciliationService sensorReconciliationService;

    private Bottle bottle;
    private BottleInstallation installation;

    @BeforeEach
    void setUp() {
        bottle = Bottle.builder()
                .id(10L)
                .tenantId(1L)
                .productId(100L)
                .batchId(5L)
                .bottleCode("BTL-001")
                .initialQuantity(new BigDecimal("100.000"))
                .currentQuantity(new BigDecimal("20.000"))
                .calibratedConsumptionPerSpray(
                        new BigDecimal("0.200")
                )
                .status(BottleStatus.INSTALLED)
                .build();

        installation = BottleInstallation.builder()
                .id(1L)
                .tenantId(1L)
                .bottleId(10L)
                .machineId(3L)
                .slotId(2L)
                .active(true)
                .build();
    }

    private RecordSensorReadingRequest buildRequest(
            String sensorEventId,
            BigDecimal sensorQuantity
    ) {
        return RecordSensorReadingRequest.builder()
                .tenantId(1L)
                .sensorEventId(sensorEventId)
                .bottleId(10L)
                .machineId(3L)
                .slotId(2L)
                .sensorQuantity(sensorQuantity)
                .measuredAt(LocalDateTime.now())
                .build();
    }

    private void mockBottleAndInstallation() {
        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(installationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.of(installation));
    }

    private void mockSave() {
        when(reconciliationRepository.save(
                any(SensorReconciliation.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldReturnNormalWhenDiscrepancyWithinTolerance() {
        RecordSensorReadingRequest request =
                buildRequest(
                        "SENSOR-001",
                        new BigDecimal("19.000")
                );

        when(reconciliationRepository
                .findByTenantIdAndSensorEventId(
                        1L,
                        "SENSOR-001"
                ))
                .thenReturn(Optional.empty());

        mockBottleAndInstallation();
        mockSave();

        SensorReconciliationResponse result =
                sensorReconciliationService.reconcile(request);

        assertEquals(
                ReconciliationStatus.NORMAL,
                result.getStatus()
        );

        assertFalse(result.getLowStock());

        assertEquals(
                95L,
                result.getEstimatedRemainingSprays()
        );
    }

    @Test
    void shouldReturnDiscrepancyWhenDifferenceExceedsTolerance() {
        RecordSensorReadingRequest request =
                buildRequest(
                        "SENSOR-002",
                        new BigDecimal("17.000")
                );

        when(reconciliationRepository
                .findByTenantIdAndSensorEventId(
                        1L,
                        "SENSOR-002"
                ))
                .thenReturn(Optional.empty());

        mockBottleAndInstallation();
        mockSave();

        SensorReconciliationResponse result =
                sensorReconciliationService.reconcile(request);

        assertEquals(
                ReconciliationStatus.DISCREPANCY,
                result.getStatus()
        );
    }

    @Test
    void shouldDetectPossibleLeak() {
        RecordSensorReadingRequest request =
                buildRequest(
                        "SENSOR-003",
                        new BigDecimal("14.000")
                );

        when(reconciliationRepository
                .findByTenantIdAndSensorEventId(
                        1L,
                        "SENSOR-003"
                ))
                .thenReturn(Optional.empty());

        mockBottleAndInstallation();
        mockSave();

        SensorReconciliationResponse result =
                sensorReconciliationService.reconcile(request);

        assertEquals(
                ReconciliationStatus.POSSIBLE_LEAK,
                result.getStatus()
        );
    }

    @Test
    void shouldDetectLowStock() {
        RecordSensorReadingRequest request =
                buildRequest(
                        "SENSOR-004",
                        new BigDecimal("1.800")
                );

        when(reconciliationRepository
                .findByTenantIdAndSensorEventId(
                        1L,
                        "SENSOR-004"
                ))
                .thenReturn(Optional.empty());

        mockBottleAndInstallation();
        mockSave();

        SensorReconciliationResponse result =
                sensorReconciliationService.reconcile(request);

        assertTrue(result.getLowStock());

        assertEquals(
                9L,
                result.getEstimatedRemainingSprays()
        );
    }

    @Test
    void shouldReturnExistingRecordForDuplicateSensorEvent() {
        SensorReconciliation existing =
                SensorReconciliation.builder()
                        .id(99L)
                        .tenantId(1L)
                        .sensorEventId("SENSOR-005")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .predictedQuantity(
                                new BigDecimal("20.000")
                        )
                        .sensorQuantity(
                                new BigDecimal("19.000")
                        )
                        .discrepancy(
                                new BigDecimal("1.000")
                        )
                        .status(ReconciliationStatus.NORMAL)
                        .lowStock(false)
                        .estimatedRemainingSprays(95L)
                        .measuredAt(LocalDateTime.now())
                        .build();

        when(reconciliationRepository
                .findByTenantIdAndSensorEventId(
                        1L,
                        "SENSOR-005"
                ))
                .thenReturn(Optional.of(existing));

        RecordSensorReadingRequest request =
                buildRequest(
                        "SENSOR-005",
                        new BigDecimal("10.000")
                );

        SensorReconciliationResponse result =
                sensorReconciliationService.reconcile(request);

        assertEquals(
                99L,
                result.getId()
        );

        assertEquals(
                0,
                new BigDecimal("19.000")
                        .compareTo(result.getSensorQuantity())
        );

        verify(bottleRepository, never())
                .findByIdAndTenantId(anyLong(), anyLong());

        verify(reconciliationRepository, never())
                .save(any());
    }

    @Test
    void shouldRejectWhenMachineOrSlotDoesNotMatchInstallation() {
        RecordSensorReadingRequest request =
                RecordSensorReadingRequest.builder()
                        .tenantId(1L)
                        .sensorEventId("SENSOR-006")
                        .bottleId(10L)
                        .machineId(99L)
                        .slotId(2L)
                        .sensorQuantity(
                                new BigDecimal("19.000")
                        )
                        .measuredAt(LocalDateTime.now())
                        .build();

        when(reconciliationRepository
                .findByTenantIdAndSensorEventId(
                        1L,
                        "SENSOR-006"
                ))
                .thenReturn(Optional.empty());

        mockBottleAndInstallation();

        assertThrows(
                SensorInstallationMismatchException.class,
                () -> sensorReconciliationService
                        .reconcile(request)
        );

        verify(reconciliationRepository, never())
                .save(any());
    }

    @Test
    void shouldNotMarkPossibleLeakWhenSensorQuantityIsHigher() {
        RecordSensorReadingRequest request =
                buildRequest(
                        "SENSOR-007",
                        new BigDecimal("26.000")
                );

        when(reconciliationRepository
                .findByTenantIdAndSensorEventId(
                        1L,
                        "SENSOR-007"
                ))
                .thenReturn(Optional.empty());

        mockBottleAndInstallation();
        mockSave();

        SensorReconciliationResponse result =
                sensorReconciliationService.reconcile(request);

        assertEquals(
                ReconciliationStatus.DISCREPANCY,
                result.getStatus()
        );
    }
}