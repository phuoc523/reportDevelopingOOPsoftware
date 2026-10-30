package com.scentstation.operation.inventory.adjustment.service;

import com.scentstation.operation.inventory.adjustment.dto.request.AdjustInventoryRequest;
import com.scentstation.operation.inventory.adjustment.entity.InventoryAdjustment;
import com.scentstation.operation.inventory.adjustment.repository.InventoryAdjustmentRepository;
import com.scentstation.operation.inventory.audit.service.InventoryAuditService;
import com.scentstation.operation.inventory.audit.entity.InventoryAuditEvent;
import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import com.scentstation.operation.inventory.movement.repository.BottleMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryAdjustmentServiceTest {

    @Mock
    private InventoryAuditService auditService;

    @Mock
    private InventoryAdjustmentRepository adjustmentRepository;

    @Mock
    private BottleRepository bottleRepository;

    @Mock
    private BottleInstallationRepository installationRepository;

    @Mock
    private BottleMovementRepository movementRepository;

    @InjectMocks
    private InventoryAdjustmentService service;

    private Bottle bottle;

    @BeforeEach
    void setUp() {
        bottle = Bottle.builder()
                .id(10L)
                .tenantId(1L)
                .currentQuantity(new BigDecimal("20.000"))
                .status(BottleStatus.INSTALLED)
                .build();
    }

    @Test
    void shouldAdjustQuantityAndCreateAuditRecords() {
        AdjustInventoryRequest request =
                AdjustInventoryRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .newQuantity(
                                new BigDecimal("17.000")
                        )
                        .actorId(25L)
                        .reason("Sensor reconciliation")
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        BottleInstallation installation =
                BottleInstallation.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .active(true)
                        .build();

        when(installationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.of(installation));

        when(adjustmentRepository.save(
                any(InventoryAdjustment.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        service.adjust(request);

        assertEquals(
                0,
                new BigDecimal("17.000")
                        .compareTo(bottle.getCurrentQuantity())
        );

        ArgumentCaptor<BottleMovement> movementCaptor =
                ArgumentCaptor.forClass(BottleMovement.class);

        verify(movementRepository)
                .save(movementCaptor.capture());

        BottleMovement movement =
                movementCaptor.getValue();

        assertEquals(
                BottleMovementType.ADJUSTMENT,
                movement.getMovementType()
        );

        assertEquals(3L, movement.getMachineId());
        assertEquals(2L, movement.getSlotId());
        assertEquals(25L, movement.getActorId());

        ArgumentCaptor<InventoryAuditEvent> auditCaptor =
                ArgumentCaptor.forClass(InventoryAuditEvent.class);
        verify(auditService).record(auditCaptor.capture());

        InventoryAuditEvent audit = auditCaptor.getValue();
        assertEquals(InventoryAuditAction.INVENTORY_ADJUSTMENT, audit.getAction());
        assertEquals(1L, audit.getTenantId());
        assertEquals(25L, audit.getActorId());
        assertEquals(10L, audit.getEntityId());
        assertEquals("20.000", audit.getBeforeValue());
        assertEquals("17.000", audit.getAfterValue());
        assertEquals("Sensor reconciliation", audit.getReason());
        assertEquals(3L, audit.getMachineId());
        assertEquals(2L, audit.getSlotId());
    }

    @Test
    void shouldSetEmptyWhenAdjustedToZero() {
        AdjustInventoryRequest request =
                AdjustInventoryRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .newQuantity(BigDecimal.ZERO)
                        .actorId(25L)
                        .reason("Bottle empty")
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(adjustmentRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adjust(request);

        assertEquals(
                BottleStatus.EMPTY,
                bottle.getStatus()
        );
    }

    @Test
    void shouldKeepDamagedStatusAfterAdjustment() {
        bottle.setStatus(BottleStatus.DAMAGED);

        AdjustInventoryRequest request =
                AdjustInventoryRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .newQuantity(
                                new BigDecimal("15.000")
                        )
                        .actorId(25L)
                        .reason("Manual recount")
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(adjustmentRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adjust(request);

        assertEquals(
                BottleStatus.DAMAGED,
                bottle.getStatus()
        );
    }

    @Test
    void shouldSetAvailableWhenPositiveAndNotInstalled() {
        bottle.setStatus(BottleStatus.EMPTY);

        AdjustInventoryRequest request =
                AdjustInventoryRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .newQuantity(
                                new BigDecimal("10.000")
                        )
                        .actorId(25L)
                        .reason("Verified stock")
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(installationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.empty());

        when(adjustmentRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adjust(request);

        assertEquals(
                BottleStatus.AVAILABLE,
                bottle.getStatus()
        );
    }
}
