package com.scentstation.operation.inventory.bottle.service;

import com.scentstation.operation.inventory.batch.entity.InventoryBatch;
import com.scentstation.operation.inventory.audit.service.InventoryAuditService;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchExpiredException;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchNotFoundException;
import com.scentstation.operation.inventory.batch.repository.InventoryBatchRepository;
import com.scentstation.operation.inventory.bottle.dto.request.CreateBottleRequest;
import com.scentstation.operation.inventory.bottle.dto.response.BottleResponse;
import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.exception.BottleAlreadyExistsException;
import com.scentstation.operation.inventory.bottle.exception.BottleBatchMismatchException;
import com.scentstation.operation.inventory.bottle.mapper.BottleMapper;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BottleServiceTest {

    @Mock
    private InventoryAuditService auditService;

    @Mock
    private BottleRepository bottleRepository;

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private BottleMapper bottleMapper;

    @InjectMocks
    private BottleService bottleService;

    private CreateBottleRequest request;
    private InventoryBatch batch;
    private Bottle bottle;
    private BottleResponse response;

    @BeforeEach
    void setUp() {
        request = CreateBottleRequest.builder()
                .tenantId(1L)
                .productId(10L)
                .batchId(5L)
                .bottleCode("BTL-001")
                .initialQuantity(new BigDecimal("100.000"))
                .build();

        batch = InventoryBatch.builder()
                .id(5L)
                .tenantId(1L)
                .productId(10L)
                .batchCode("BATCH-001")
                .receivedQuantity(20)
                .receivedAt(LocalDate.of(2026, 10, 20))
                .expiryDate(LocalDate.of(2028, 10, 20))
                .build();

        bottle = Bottle.builder()
                .id(1L)
                .tenantId(1L)
                .productId(10L)
                .batchId(5L)
                .bottleCode("BTL-001")
                .initialQuantity(new BigDecimal("100.000"))
                .currentQuantity(new BigDecimal("100.000"))
                .status(BottleStatus.AVAILABLE)
                .build();

        response = BottleResponse.builder()
                .id(1L)
                .tenantId(1L)
                .productId(10L)
                .batchId(5L)
                .bottleCode("BTL-001")
                .initialQuantity(new BigDecimal("100.000"))
                .currentQuantity(new BigDecimal("100.000"))
                .status(BottleStatus.AVAILABLE)
                .build();
    }

    @Test
    void shouldCreateBottleSuccessfully() {
        when(bottleRepository.existsByTenantIdAndBottleCode(
                1L,
                "BTL-001"
        )).thenReturn(false);

        when(inventoryBatchRepository.findByIdAndTenantId(5L, 1L))
                .thenReturn(Optional.of(batch));

        when(bottleMapper.toEntity(request))
                .thenReturn(bottle);

        when(bottleRepository.save(bottle))
                .thenReturn(bottle);

        when(bottleMapper.toResponse(bottle))
                .thenReturn(response);

        BottleResponse result =
                bottleService.createBottle(request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("BTL-001", result.getBottleCode());
        assertEquals(BottleStatus.AVAILABLE, result.getStatus());

        verify(bottleRepository).save(bottle);
    }

    @Test
    void shouldRejectDuplicateBottleCode() {
        when(bottleRepository.existsByTenantIdAndBottleCode(
                1L,
                "BTL-001"
        )).thenReturn(true);

        assertThrows(
                BottleAlreadyExistsException.class,
                () -> bottleService.createBottle(request)
        );

        verify(bottleRepository, never()).save(any());
    }

    @Test
    void shouldRejectBottleWhenBatchDoesNotExistForTenant() {
        when(bottleRepository.existsByTenantIdAndBottleCode(
                1L,
                "BTL-001"
        )).thenReturn(false);

        when(inventoryBatchRepository.findByIdAndTenantId(5L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(
                InventoryBatchNotFoundException.class,
                () -> bottleService.createBottle(request)
        );

        verify(bottleRepository, never()).save(any());
    }

    @Test
    void shouldRejectBottleWhenProductDoesNotMatchBatch() {
        batch.setProductId(99L);

        when(bottleRepository.existsByTenantIdAndBottleCode(
                1L,
                "BTL-001"
        )).thenReturn(false);

        when(inventoryBatchRepository.findByIdAndTenantId(5L, 1L))
                .thenReturn(Optional.of(batch));

        assertThrows(
                BottleBatchMismatchException.class,
                () -> bottleService.createBottle(request)
        );

        verify(bottleRepository, never()).save(any());
    }

    @Test
    void shouldRejectBottleWhenBatchIsExpired() {
        batch.setExpiryDate(LocalDate.of(2025, 10, 20));

        when(bottleRepository.existsByTenantIdAndBottleCode(
                1L,
                "BTL-001"
        )).thenReturn(false);

        when(inventoryBatchRepository.findByIdAndTenantId(5L, 1L))
                .thenReturn(Optional.of(batch));

        assertThrows(
                InventoryBatchExpiredException.class,
                () -> bottleService.createBottle(request)
        );

        verify(bottleRepository, never()).save(any());
    }
}
