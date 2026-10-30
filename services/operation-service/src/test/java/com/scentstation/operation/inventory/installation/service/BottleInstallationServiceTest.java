package com.scentstation.operation.inventory.installation.service;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.audit.service.InventoryAuditService;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.installation.dto.request.InstallBottleRequest;
import com.scentstation.operation.inventory.installation.dto.request.RemoveBottleRequest;
import com.scentstation.operation.inventory.installation.dto.request.ReplaceBottleRequest;
import com.scentstation.operation.inventory.installation.dto.response.BottleInstallationResponse;
import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import com.scentstation.operation.inventory.installation.exception.ActiveInstallationNotFoundException;
import com.scentstation.operation.inventory.installation.exception.BottleAlreadyInstalledException;
import com.scentstation.operation.inventory.installation.exception.BottleNotInstallableException;
import com.scentstation.operation.inventory.installation.exception.SlotAlreadyOccupiedException;
import com.scentstation.operation.inventory.installation.mapper.BottleInstallationMapper;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import com.scentstation.operation.inventory.integration.machine.client.MachineSlotClient;
import com.scentstation.operation.inventory.integration.machine.dto.MachineSlotInfo;
import com.scentstation.operation.inventory.integration.machine.exception.MachineSlotProductMismatchException;
import com.scentstation.operation.inventory.integration.machine.exception.MachineSlotUnavailableException;
import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.repository.BottleMovementRepository;
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
class BottleInstallationServiceTest {

    @Mock
    private InventoryAuditService auditService;

    @Mock
    private BottleRepository bottleRepository;

    @Mock
    private BottleInstallationRepository bottleInstallationRepository;

    @Mock
    private BottleMovementRepository bottleMovementRepository;

    @Mock
    private BottleInstallationMapper bottleInstallationMapper;

    @Mock
    private MachineSlotClient machineSlotClient;

    @InjectMocks
    private BottleInstallationService bottleInstallationService;

    private Bottle bottle;
    private BottleInstallation installation;
    private BottleInstallationResponse response;

    @BeforeEach
    void setUp() {
        bottle = Bottle.builder()
                .id(10L)
                .tenantId(1L)
                .productId(100L)
                .batchId(5L)
                .bottleCode("BTL-001")
                .initialQuantity(new BigDecimal("100.000"))
                .currentQuantity(new BigDecimal("100.000"))
                .status(BottleStatus.AVAILABLE)
                .build();

        installation = BottleInstallation.builder()
                .id(1L)
                .tenantId(1L)
                .bottleId(10L)
                .machineId(3L)
                .slotId(2L)
                .installedBy(5L)
                .active(true)
                .build();

        response = BottleInstallationResponse.builder()
                .id(1L)
                .tenantId(1L)
                .bottleId(10L)
                .machineId(3L)
                .slotId(2L)
                .installedBy(5L)
                .active(true)
                .build();
    }

    @Test
    void shouldInstallBottleSuccessfully() {
        InstallBottleRequest request =
                InstallBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .actorId(5L)
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .existsByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(false);

        when(bottleInstallationRepository
                .existsByTenantIdAndMachineIdAndSlotIdAndActiveTrue(
                        1L,
                        3L,
                        2L
                ))
                .thenReturn(false);

        when(machineSlotClient.getSlot(1L, 3L, 2L))
                .thenReturn(machineSlot(100L, true, false));

        when(bottleInstallationMapper.toEntity(request))
                .thenReturn(installation);

        when(bottleInstallationRepository.save(installation))
                .thenReturn(installation);

        when(bottleInstallationMapper.toResponse(installation))
                .thenReturn(response);

        BottleInstallationResponse result =
                bottleInstallationService.installBottle(request);

        assertNotNull(result);
        assertEquals(10L, result.getBottleId());
        assertEquals(3L, result.getMachineId());
        assertEquals(2L, result.getSlotId());

        assertEquals(
                BottleStatus.INSTALLED,
                bottle.getStatus()
        );

        verify(bottleRepository).save(bottle);
        verify(bottleMovementRepository)
                .save(any(BottleMovement.class));
        verify(auditService).record(any());
    }

    @Test
    void shouldInstallBottleWhenSlotProductIsUnspecified() {
        InstallBottleRequest request = installRequest();

        stubAvailableBottleAndSlot(request);
        when(machineSlotClient.getSlot(1L, 3L, 2L))
                .thenReturn(machineSlot(null, true, false));
        when(bottleInstallationMapper.toEntity(request))
                .thenReturn(installation);
        when(bottleInstallationRepository.save(installation))
                .thenReturn(installation);
        when(bottleInstallationMapper.toResponse(installation))
                .thenReturn(response);

        BottleInstallationResponse result =
                bottleInstallationService.installBottle(request);

        assertNotNull(result);
        assertEquals(10L, result.getBottleId());
    }

    @Test
    void shouldRejectInstallWhenSlotIsInactive() {
        InstallBottleRequest request = installRequest();

        stubAvailableBottleAndSlot(request);
        when(machineSlotClient.getSlot(1L, 3L, 2L))
                .thenReturn(machineSlot(100L, false, false));

        assertThrows(
                MachineSlotUnavailableException.class,
                () -> bottleInstallationService.installBottle(request)
        );

        verify(bottleInstallationRepository, never()).save(any());
    }

    @Test
    void shouldRejectInstallWhenSlotIsInMaintenanceMode() {
        InstallBottleRequest request = installRequest();

        stubAvailableBottleAndSlot(request);
        when(machineSlotClient.getSlot(1L, 3L, 2L))
                .thenReturn(machineSlot(100L, true, true));

        assertThrows(
                MachineSlotUnavailableException.class,
                () -> bottleInstallationService.installBottle(request)
        );

        verify(bottleInstallationRepository, never()).save(any());
    }

    @Test
    void shouldRejectInstallWhenSlotProductDoesNotMatch() {
        InstallBottleRequest request = installRequest();

        stubAvailableBottleAndSlot(request);
        when(machineSlotClient.getSlot(1L, 3L, 2L))
                .thenReturn(machineSlot(200L, true, false));

        assertThrows(
                MachineSlotProductMismatchException.class,
                () -> bottleInstallationService.installBottle(request)
        );

        verify(bottleInstallationRepository, never()).save(any());
    }

    @Test
    void shouldRejectInstallWhenBottleIsNotAvailable() {
        bottle.setStatus(BottleStatus.DAMAGED);

        InstallBottleRequest request =
                InstallBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .actorId(5L)
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        assertThrows(
                BottleNotInstallableException.class,
                () -> bottleInstallationService.installBottle(request)
        );

        verify(
                bottleInstallationRepository,
                never()
        ).save(any());
    }

    @Test
    void shouldRejectInstallWhenBottleAlreadyInstalled() {
        InstallBottleRequest request =
                InstallBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .actorId(5L)
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .existsByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(true);

        assertThrows(
                BottleAlreadyInstalledException.class,
                () -> bottleInstallationService.installBottle(request)
        );
    }

    @Test
    void shouldRejectInstallWhenSlotIsOccupied() {
        InstallBottleRequest request =
                InstallBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .actorId(5L)
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .existsByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(false);

        when(bottleInstallationRepository
                .existsByTenantIdAndMachineIdAndSlotIdAndActiveTrue(
                        1L,
                        3L,
                        2L
                ))
                .thenReturn(true);

        assertThrows(
                SlotAlreadyOccupiedException.class,
                () -> bottleInstallationService.installBottle(request)
        );
    }

    @Test
    void shouldRemoveBottleSuccessfully() {
        bottle.setStatus(BottleStatus.INSTALLED);

        RemoveBottleRequest request =
                RemoveBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .actorId(5L)
                        .reason("Replace bottle")
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.of(installation));

        when(bottleInstallationRepository.save(installation))
                .thenReturn(installation);

        when(bottleInstallationMapper.toResponse(installation))
                .thenReturn(response);

        bottleInstallationService.removeBottle(request);

        assertFalse(installation.getActive());
        assertNotNull(installation.getRemovedAt());

        assertEquals(
                BottleStatus.AVAILABLE,
                bottle.getStatus()
        );

        verify(bottleMovementRepository)
                .save(any(BottleMovement.class));
        verify(auditService).record(any());
        verifyNoInteractions(machineSlotClient);
    }

    @Test
    void shouldRejectRemoveWhenNoActiveInstallationExists() {
        RemoveBottleRequest request =
                RemoveBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .actorId(5L)
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.empty());

        assertThrows(
                ActiveInstallationNotFoundException.class,
                () -> bottleInstallationService.removeBottle(request)
        );
    }

    @Test
    void shouldReplaceBottleSuccessfully() {
        bottle.setStatus(BottleStatus.INSTALLED);

        Bottle newBottle = Bottle.builder()
                .id(20L)
                .tenantId(1L)
                .productId(100L)
                .batchId(6L)
                .bottleCode("BTL-002")
                .initialQuantity(new BigDecimal("100.000"))
                .currentQuantity(new BigDecimal("100.000"))
                .status(BottleStatus.AVAILABLE)
                .build();

        ReplaceBottleRequest request =
                ReplaceBottleRequest.builder()
                        .tenantId(1L)
                        .oldBottleId(10L)
                        .newBottleId(20L)
                        .actorId(5L)
                        .reason("Old bottle nearly empty")
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.of(installation));

        when(bottleRepository.findByIdAndTenantId(20L, 1L))
                .thenReturn(Optional.of(newBottle));

        when(bottleInstallationRepository
                .existsByTenantIdAndBottleIdAndActiveTrue(1L, 20L))
                .thenReturn(false);

        when(machineSlotClient.getSlot(1L, 3L, 2L))
                .thenReturn(machineSlot(100L, true, false));

        when(bottleInstallationRepository.save(any(BottleInstallation.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(bottleInstallationMapper.toResponse(
                any(BottleInstallation.class)
        )).thenAnswer(invocation -> {
            BottleInstallation value =
                    invocation.getArgument(0);

            return BottleInstallationResponse.builder()
                    .tenantId(value.getTenantId())
                    .bottleId(value.getBottleId())
                    .machineId(value.getMachineId())
                    .slotId(value.getSlotId())
                    .active(value.getActive())
                    .build();
        });

        BottleInstallationResponse result =
                bottleInstallationService.replaceBottle(request);

        assertEquals(20L, result.getBottleId());

        assertEquals(
                BottleStatus.AVAILABLE,
                bottle.getStatus()
        );

        assertEquals(
                BottleStatus.INSTALLED,
                newBottle.getStatus()
        );

        assertFalse(installation.getActive());

        verify(bottleMovementRepository, times(2))
                .save(any(BottleMovement.class));
        verify(auditService, times(2)).record(any());
    }

    @Test
    void shouldRejectReplaceWhenSlotProductDoesNotMatch() {
        bottle.setStatus(BottleStatus.INSTALLED);

        Bottle newBottle = Bottle.builder()
                .id(20L)
                .tenantId(1L)
                .productId(200L)
                .status(BottleStatus.AVAILABLE)
                .build();

        ReplaceBottleRequest request = ReplaceBottleRequest.builder()
                .tenantId(1L)
                .oldBottleId(10L)
                .newBottleId(20L)
                .actorId(5L)
                .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));
        when(bottleInstallationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.of(installation));
        when(bottleRepository.findByIdAndTenantId(20L, 1L))
                .thenReturn(Optional.of(newBottle));
        when(bottleInstallationRepository
                .existsByTenantIdAndBottleIdAndActiveTrue(1L, 20L))
                .thenReturn(false);
        when(machineSlotClient.getSlot(1L, 3L, 2L))
                .thenReturn(machineSlot(100L, true, false));

        assertThrows(
                MachineSlotProductMismatchException.class,
                () -> bottleInstallationService.replaceBottle(request)
        );

        assertTrue(installation.getActive());
        verify(bottleInstallationRepository, never()).save(any());
        verify(bottleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    private InstallBottleRequest installRequest() {
        return InstallBottleRequest.builder()
                .tenantId(1L)
                .bottleId(10L)
                .machineId(3L)
                .slotId(2L)
                .actorId(5L)
                .build();
    }

    private void stubAvailableBottleAndSlot(
            InstallBottleRequest request
    ) {
        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));
        when(bottleInstallationRepository
                .existsByTenantIdAndBottleIdAndActiveTrue(1L, 10L))
                .thenReturn(false);
        when(bottleInstallationRepository
                .existsByTenantIdAndMachineIdAndSlotIdAndActiveTrue(
                        1L,
                        3L,
                        2L
                ))
                .thenReturn(false);
    }

    private MachineSlotInfo machineSlot(
            Long productId,
            boolean active,
            boolean maintenanceMode
    ) {
        return MachineSlotInfo.builder()
                .tenantId(1L)
                .machineId(3L)
                .slotId(2L)
                .productId(productId)
                .active(active)
                .maintenanceMode(maintenanceMode)
                .build();
    }
}
