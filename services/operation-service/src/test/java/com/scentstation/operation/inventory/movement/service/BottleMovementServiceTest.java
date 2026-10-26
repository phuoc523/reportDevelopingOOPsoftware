package com.scentstation.operation.inventory.movement.service;

import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.movement.dto.response.BottleMovementResponse;
import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import com.scentstation.operation.inventory.movement.repository.BottleMovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BottleMovementServiceTest {

    @Mock
    private BottleMovementRepository movementRepository;

    @Mock
    private BottleRepository bottleRepository;

    @InjectMocks
    private BottleMovementService movementService;

    @Test
    void shouldReturnBottleMovementHistory() {
        Bottle bottle =
                Bottle.builder()
                        .id(10L)
                        .tenantId(1L)
                        .build();

        BottleMovement movement =
                BottleMovement.builder()
                        .id(1L)
                        .tenantId(1L)
                        .bottleId(10L)
                        .movementType(
                                BottleMovementType.ADJUSTMENT
                        )
                        .quantityBefore(
                                new BigDecimal("20.000")
                        )
                        .quantityAfter(
                                new BigDecimal("17.000")
                        )
                        .actorId(25L)
                        .reason("Sensor reconciliation")
                        .build();

        when(bottleRepository
                .findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(bottle));

        when(movementRepository
                .findAllByTenantIdAndBottleIdOrderByCreatedAtDesc(
                        1L,
                        10L
                ))
                .thenReturn(List.of(movement));

        List<BottleMovementResponse> result =
                movementService.getBottleHistory(
                        1L,
                        10L
                );

        assertEquals(1, result.size());

        assertEquals(
                BottleMovementType.ADJUSTMENT,
                result.get(0).getMovementType()
        );

        assertEquals(
                0,
                new BigDecimal("-3.000")
                        .compareTo(
                                result.get(0).getDifference()
                        )
        );
    }

    @Test
    void shouldRejectBottleHistoryWhenBottleDoesNotExist() {
        when(bottleRepository
                .findByIdAndTenantId(999L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(
                BottleNotFoundException.class,
                () -> movementService
                        .getBottleHistory(
                                1L,
                                999L
                        )
        );

        verifyNoInteractions(movementRepository);
    }

    @Test
    void shouldReturnHistoryFilteredByType() {
        BottleMovement movement =
                BottleMovement.builder()
                        .id(1L)
                        .tenantId(1L)
                        .bottleId(10L)
                        .movementType(
                                BottleMovementType.REFILL
                        )
                        .quantityBefore(
                                new BigDecimal("10.000")
                        )
                        .quantityAfter(
                                new BigDecimal("50.000")
                        )
                        .build();

        when(movementRepository
                .findAllByTenantIdAndMovementTypeOrderByCreatedAtDesc(
                        1L,
                        BottleMovementType.REFILL
                ))
                .thenReturn(List.of(movement));

        List<BottleMovementResponse> result =
                movementService.getHistoryByType(
                        1L,
                        BottleMovementType.REFILL
                );

        assertEquals(1, result.size());

        assertEquals(
                BottleMovementType.REFILL,
                result.get(0).getMovementType()
        );
    }
}