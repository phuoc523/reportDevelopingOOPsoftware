package com.scentstation.operation.inventory.refill.service;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.repository.BottleMovementRepository;
import com.scentstation.operation.inventory.refill.dto.request.RefillBottleRequest;
import com.scentstation.operation.inventory.refill.dto.response.RefillBottleResponse;
import com.scentstation.operation.inventory.refill.exception.BottleNotRefillableException;
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
class RefillServiceTest {

    @Mock
    private BottleRepository bottleRepository;

    @Mock
    private BottleInstallationRepository bottleInstallationRepository;

    @Mock
    private BottleMovementRepository bottleMovementRepository;

    @InjectMocks
    private RefillService refillService;

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
                .currentQuantity(new BigDecimal("20.000"))
                .status(BottleStatus.NEARLY_EMPTY)
                .build();
    }

    @Test
    void shouldRefillAvailableBottleSuccessfully() {
        RefillBottleRequest request =
                RefillBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .refillQuantity(new BigDecimal("50.000"))
                        .actorId(5L)
                        .reason("Routine refill")
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .existsByTenantIdAndBottleIdAndActiveTrue(
                        1L,
                        10L
                ))
                .thenReturn(false);

        RefillBottleResponse result =
                refillService.refillBottle(request);

        assertNotNull(result);

        assertEquals(
                0,
                new BigDecimal("20.000")
                        .compareTo(result.getQuantityBefore())
        );

        assertEquals(
                0,
                new BigDecimal("50.000")
                        .compareTo(result.getRefillQuantity())
        );

        assertEquals(
                0,
                new BigDecimal("70.000")
                        .compareTo(result.getQuantityAfter())
        );

        assertEquals(
                BottleStatus.AVAILABLE,
                result.getStatus()
        );

        verify(bottleRepository).save(bottle);

        verify(bottleMovementRepository)
                .save(any(BottleMovement.class));
    }

    @Test
    void shouldKeepInstalledStatusAfterRefill() {
        bottle.setStatus(BottleStatus.INSTALLED);

        RefillBottleRequest request =
                RefillBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .refillQuantity(new BigDecimal("50.000"))
                        .actorId(5L)
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .existsByTenantIdAndBottleIdAndActiveTrue(
                        1L,
                        10L
                ))
                .thenReturn(true);

        RefillBottleResponse result =
                refillService.refillBottle(request);

        assertEquals(
                BottleStatus.INSTALLED,
                result.getStatus()
        );

        assertEquals(
                BottleStatus.INSTALLED,
                bottle.getStatus()
        );
    }

    @Test
    void shouldAllowRefillWhenBottleIsEmpty() {
        bottle.setCurrentQuantity(BigDecimal.ZERO);
        bottle.setStatus(BottleStatus.EMPTY);

        RefillBottleRequest request =
                RefillBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .refillQuantity(new BigDecimal("100.000"))
                        .actorId(5L)
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(bottleInstallationRepository
                .existsByTenantIdAndBottleIdAndActiveTrue(
                        1L,
                        10L
                ))
                .thenReturn(false);

        RefillBottleResponse result =
                refillService.refillBottle(request);

        assertEquals(
                0,
                new BigDecimal("100.000")
                        .compareTo(result.getQuantityAfter())
        );

        assertEquals(
                BottleStatus.AVAILABLE,
                result.getStatus()
        );
    }

    @Test
    void shouldRejectRefillWhenBottleIsDamaged() {
        bottle.setStatus(BottleStatus.DAMAGED);

        RefillBottleRequest request =
                RefillBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .refillQuantity(new BigDecimal("50.000"))
                        .actorId(5L)
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        assertThrows(
                BottleNotRefillableException.class,
                () -> refillService.refillBottle(request)
        );

        verify(bottleRepository, never()).save(any());
        verify(bottleMovementRepository, never()).save(any());
    }

    @Test
    void shouldRejectRefillWhenBottleIsExpired() {
        bottle.setStatus(BottleStatus.EXPIRED);

        RefillBottleRequest request =
                RefillBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .refillQuantity(new BigDecimal("50.000"))
                        .actorId(5L)
                        .build();

        when(bottleRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        assertThrows(
                BottleNotRefillableException.class,
                () -> refillService.refillBottle(request)
        );

        verify(bottleRepository, never()).save(any());
    }

    @Test
    void shouldRejectRefillWhenBottleDoesNotExistForTenant() {
        RefillBottleRequest request =
                RefillBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(999L)
                        .refillQuantity(new BigDecimal("50.000"))
                        .actorId(5L)
                        .build();

        when(bottleRepository.findByIdAndTenantId(
                999L,
                1L
        )).thenReturn(Optional.empty());

        assertThrows(
                BottleNotFoundException.class,
                () -> refillService.refillBottle(request)
        );

        verify(bottleRepository, never()).save(any());
    }
}