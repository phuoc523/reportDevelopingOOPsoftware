package com.scentstation.operation.inventory.batch.service;

import com.scentstation.operation.inventory.batch.dto.request.CreateInventoryBatchRequest;
import com.scentstation.operation.inventory.audit.service.InventoryAuditService;
import com.scentstation.operation.inventory.batch.dto.response.InventoryBatchResponse;
import com.scentstation.operation.inventory.batch.entity.InventoryBatch;
import com.scentstation.operation.inventory.batch.exception.InvalidInventoryBatchDateException;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchAlreadyExistsException;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchNotFoundException;
import com.scentstation.operation.inventory.batch.mapper.InventoryBatchMapper;
import com.scentstation.operation.inventory.batch.repository.InventoryBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryBatchServiceTest {

    @Mock
    private InventoryAuditService auditService;

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private InventoryBatchMapper inventoryBatchMapper;

    @InjectMocks
    private InventoryBatchService inventoryBatchService;

    private CreateInventoryBatchRequest validRequest;
    private InventoryBatch batch;
    private InventoryBatchResponse response;

    @BeforeEach
    void setUp() {
        validRequest = CreateInventoryBatchRequest.builder()
                .tenantId(1L)
                .productId(10L)
                .batchCode("BATCH-001")
                .receivedQuantity(20)
                .receivedAt(LocalDate.of(2026, 10, 20))
                .expiryDate(LocalDate.of(2028, 10, 20))
                .build();

        batch = InventoryBatch.builder()
                .id(1L)
                .tenantId(1L)
                .productId(10L)
                .batchCode("BATCH-001")
                .receivedQuantity(20)
                .receivedAt(LocalDate.of(2026, 10, 20))
                .expiryDate(LocalDate.of(2028, 10, 20))
                .build();

        response = InventoryBatchResponse.builder()
                .id(1L)
                .tenantId(1L)
                .productId(10L)
                .batchCode("BATCH-001")
                .receivedQuantity(20)
                .receivedAt(LocalDate.of(2026, 10, 20))
                .expiryDate(LocalDate.of(2028, 10, 20))
                .expired(false)
                .build();
    }

    @Test
    void shouldCreateBatchSuccessfully() {
        when(inventoryBatchRepository.existsByTenantIdAndBatchCode(
                1L,
                "BATCH-001"
        )).thenReturn(false);

        when(inventoryBatchMapper.toEntity(validRequest))
                .thenReturn(batch);

        when(inventoryBatchRepository.save(batch))
                .thenReturn(batch);

        when(inventoryBatchMapper.toResponse(batch))
                .thenReturn(response);

        InventoryBatchResponse result =
                inventoryBatchService.createBatch(validRequest);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("BATCH-001", result.getBatchCode());
        assertEquals(20, result.getReceivedQuantity());

        verify(inventoryBatchRepository).save(batch);
    }

    @Test
    void shouldRejectDuplicateBatchCodeWithinSameTenant() {
        when(inventoryBatchRepository.existsByTenantIdAndBatchCode(
                1L,
                "BATCH-001"
        )).thenReturn(true);

        assertThrows(
                InventoryBatchAlreadyExistsException.class,
                () -> inventoryBatchService.createBatch(validRequest)
        );

        verify(inventoryBatchRepository, never())
                .save(any(InventoryBatch.class));
    }

    @Test
    void shouldRejectExpiryDateBeforeReceivedDate() {
        CreateInventoryBatchRequest invalidRequest =
                CreateInventoryBatchRequest.builder()
                        .tenantId(1L)
                        .productId(10L)
                        .batchCode("BATCH-002")
                        .receivedQuantity(20)
                        .receivedAt(LocalDate.of(2026, 10, 20))
                        .expiryDate(LocalDate.of(2026, 10, 19))
                        .build();

        when(inventoryBatchRepository.existsByTenantIdAndBatchCode(
                1L,
                "BATCH-002"
        )).thenReturn(false);

        assertThrows(
                InvalidInventoryBatchDateException.class,
                () -> inventoryBatchService.createBatch(invalidRequest)
        );

        verify(inventoryBatchRepository, never())
                .save(any(InventoryBatch.class));
    }

    @Test
    void shouldGetBatchesByTenant() {
        when(inventoryBatchRepository.findAllByTenantId(1L))
                .thenReturn(List.of(batch));

        when(inventoryBatchMapper.toResponse(batch))
                .thenReturn(response);

        List<InventoryBatchResponse> result =
                inventoryBatchService.getBatchesByTenant(1L);

        assertEquals(1, result.size());
        assertEquals("BATCH-001", result.getFirst().getBatchCode());
    }

    @Test
    void shouldGetBatchesByTenantAndProduct() {
        when(inventoryBatchRepository
                .findAllByTenantIdAndProductId(1L, 10L))
                .thenReturn(List.of(batch));

        when(inventoryBatchMapper.toResponse(batch))
                .thenReturn(response);

        List<InventoryBatchResponse> result =
                inventoryBatchService
                        .getBatchesByTenantAndProduct(1L, 10L);

        assertEquals(1, result.size());
        assertEquals(10L, result.getFirst().getProductId());
    }

    @Test
    void shouldGetBatchByIdAndTenant() {
        when(inventoryBatchRepository.findByIdAndTenantId(1L, 1L))
                .thenReturn(Optional.of(batch));

        when(inventoryBatchMapper.toResponse(batch))
                .thenReturn(response);

        InventoryBatchResponse result =
                inventoryBatchService.getBatchById(1L, 1L);

        assertEquals(1L, result.getId());
        assertEquals(1L, result.getTenantId());
    }

    @Test
    void shouldThrowWhenBatchDoesNotBelongToTenant() {
        when(inventoryBatchRepository.findByIdAndTenantId(1L, 2L))
                .thenReturn(Optional.empty());

        assertThrows(
                InventoryBatchNotFoundException.class,
                () -> inventoryBatchService.getBatchById(2L, 1L)
        );
    }
}
