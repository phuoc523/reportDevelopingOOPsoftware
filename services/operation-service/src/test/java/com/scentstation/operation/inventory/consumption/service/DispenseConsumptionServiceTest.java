package com.scentstation.operation.inventory.consumption.service;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.audit.service.InventoryAuditService;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.consumption.dto.request.RecordDispenseRequest;
import com.scentstation.operation.inventory.consumption.dto.response.DispenseConsumptionResponse;
import com.scentstation.operation.inventory.consumption.entity.DispenseConsumption;
import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.enums.DispenseType;
import com.scentstation.operation.inventory.consumption.exception.BottleInstallationMismatchException;
import com.scentstation.operation.inventory.consumption.exception.DuplicateDispenseCommandException;
import com.scentstation.operation.inventory.consumption.exception.InsufficientBottleQuantityException;
import com.scentstation.operation.inventory.consumption.repository.DispenseConsumptionRepository;
import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispenseConsumptionServiceTest {

    @Mock
    private InventoryAuditService auditService;

    @Mock
    private DispenseConsumptionRepository dispenseConsumptionRepository;

    @Mock
    private BottleRepository bottleRepository;

    @Mock
    private BottleInstallationRepository bottleInstallationRepository;

    @InjectMocks
    private DispenseConsumptionService dispenseConsumptionService;

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

    @Test
    void shouldDeductInventoryOnSuccessfulCustomerDispense() {
        RecordDispenseRequest request =
                RecordDispenseRequest.builder()
                        .tenantId(1L)
                        .commandId("CMD-001")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .dispenseType(DispenseType.CUSTOMER)
                        .result(DispenseResult.SUCCESS)
                        .consumedQuantity(new BigDecimal("0.200"))
                        .build();

        when(dispenseConsumptionRepository
                .existsByTenantIdAndCommandId(1L, "CMD-001"))
                .thenReturn(false);

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.of(installation));

        when(dispenseConsumptionRepository.save(
                any(DispenseConsumption.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        DispenseConsumptionResponse result =
                dispenseConsumptionService.recordDispense(request);

        assertTrue(result.getInventoryDeducted());

        assertEquals(
                0,
                new BigDecimal("19.800")
                        .compareTo(result.getQuantityAfter())
        );

        verify(bottleRepository).save(bottle);
        verify(auditService).record(any());
    }

    @Test
    void shouldNotDeductInventoryOnFailedDispense() {
        RecordDispenseRequest request =
                RecordDispenseRequest.builder()
                        .tenantId(1L)
                        .commandId("CMD-002")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .dispenseType(DispenseType.CUSTOMER)
                        .result(DispenseResult.FAILED)
                        .consumedQuantity(new BigDecimal("0.200"))
                        .build();

        when(dispenseConsumptionRepository
                .existsByTenantIdAndCommandId(1L, "CMD-002"))
                .thenReturn(false);

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.of(installation));

        when(dispenseConsumptionRepository.save(
                any(DispenseConsumption.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        DispenseConsumptionResponse result =
                dispenseConsumptionService.recordDispense(request);

        assertFalse(result.getInventoryDeducted());

        assertEquals(
                0,
                new BigDecimal("20.000")
                        .compareTo(result.getQuantityAfter())
        );

        verify(bottleRepository, never()).save(any());
    }

    @Test
    void shouldRejectDuplicateCommand() {
        RecordDispenseRequest request =
                RecordDispenseRequest.builder()
                        .tenantId(1L)
                        .commandId("CMD-001")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .dispenseType(DispenseType.CUSTOMER)
                        .result(DispenseResult.SUCCESS)
                        .consumedQuantity(new BigDecimal("0.200"))
                        .build();

        when(dispenseConsumptionRepository
                .existsByTenantIdAndCommandId(1L, "CMD-001"))
                .thenReturn(true);

        assertThrows(
                DuplicateDispenseCommandException.class,
                () -> dispenseConsumptionService.recordDispense(request)
        );

        verify(bottleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void shouldRejectWhenInstallationDoesNotMatchMachineOrSlot() {
        RecordDispenseRequest request =
                RecordDispenseRequest.builder()
                        .tenantId(1L)
                        .commandId("CMD-003")
                        .bottleId(10L)
                        .machineId(99L)
                        .slotId(2L)
                        .dispenseType(DispenseType.CUSTOMER)
                        .result(DispenseResult.SUCCESS)
                        .consumedQuantity(new BigDecimal("0.200"))
                        .build();

        when(dispenseConsumptionRepository
                .existsByTenantIdAndCommandId(1L, "CMD-003"))
                .thenReturn(false);

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.of(installation));

        assertThrows(
                BottleInstallationMismatchException.class,
                () -> dispenseConsumptionService.recordDispense(request)
        );

        verify(bottleRepository, never()).save(any());
    }

    @Test
    void shouldRejectWhenBottleQuantityIsInsufficient() {
        bottle.setCurrentQuantity(new BigDecimal("0.100"));

        RecordDispenseRequest request =
                RecordDispenseRequest.builder()
                        .tenantId(1L)
                        .commandId("CMD-004")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .dispenseType(DispenseType.CUSTOMER)
                        .result(DispenseResult.SUCCESS)
                        .consumedQuantity(new BigDecimal("0.200"))
                        .build();

        when(dispenseConsumptionRepository
                .existsByTenantIdAndCommandId(1L, "CMD-004"))
                .thenReturn(false);

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.of(installation));

        assertThrows(
                InsufficientBottleQuantityException.class,
                () -> dispenseConsumptionService.recordDispense(request)
        );

        verify(bottleRepository, never()).save(any());
    }

    @Test
    void shouldSetBottleEmptyWhenQuantityReachesZero() {
        bottle.setCurrentQuantity(new BigDecimal("0.200"));

        RecordDispenseRequest request =
                RecordDispenseRequest.builder()
                        .tenantId(1L)
                        .commandId("CMD-005")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .dispenseType(DispenseType.CUSTOMER)
                        .result(DispenseResult.SUCCESS)
                        .consumedQuantity(new BigDecimal("0.200"))
                        .build();

        when(dispenseConsumptionRepository
                .existsByTenantIdAndCommandId(1L, "CMD-005"))
                .thenReturn(false);

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.of(installation));

        when(dispenseConsumptionRepository.save(
                any(DispenseConsumption.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        dispenseConsumptionService.recordDispense(request);

        assertEquals(
                BottleStatus.EMPTY,
                bottle.getStatus()
        );
    }
}
