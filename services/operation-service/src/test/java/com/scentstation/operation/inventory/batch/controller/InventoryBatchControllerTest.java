package com.scentstation.operation.inventory.batch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.inventory.batch.dto.request.CreateInventoryBatchRequest;
import com.scentstation.operation.inventory.batch.dto.response.InventoryBatchResponse;
import com.scentstation.operation.inventory.batch.exception.InvalidInventoryBatchDateException;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchAlreadyExistsException;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchNotFoundException;
import com.scentstation.operation.inventory.batch.service.InventoryBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.scentstation.operation.inventory.batch.exception.OperationExceptionHandler;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryBatchController.class)
@Import(OperationExceptionHandler.class)
class InventoryBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryBatchService inventoryBatchService;

    @Test
    void shouldCreateInventoryBatch() throws Exception {
        CreateInventoryBatchRequest request =
                CreateInventoryBatchRequest.builder()
                        .tenantId(1L)
                        .productId(10L)
                        .batchCode("BATCH-001")
                        .receivedQuantity(20)
                        .receivedAt(LocalDate.of(2026, 10, 20))
                        .expiryDate(LocalDate.of(2028, 10, 20))
                        .build();

        InventoryBatchResponse response =
                InventoryBatchResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .productId(10L)
                        .batchCode("BATCH-001")
                        .receivedQuantity(20)
                        .receivedAt(LocalDate.of(2026, 10, 20))
                        .expiryDate(LocalDate.of(2028, 10, 20))
                        .expired(false)
                        .build();

        when(inventoryBatchService.createBatch(
                any(CreateInventoryBatchRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/inventory/batches")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.batchCode").value("BATCH-001"))
                .andExpect(jsonPath("$.receivedQuantity").value(20))
                .andExpect(jsonPath("$.expired").value(false));
    }

    @Test
    void shouldReturnBatchesByTenant() throws Exception {
        InventoryBatchResponse response =
                InventoryBatchResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .productId(10L)
                        .batchCode("BATCH-001")
                        .receivedQuantity(20)
                        .expired(false)
                        .build();

        when(inventoryBatchService.getBatchesByTenant(1L))
                .thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/inventory/batches")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].batchCode").value("BATCH-001"));
    }

    @Test
    void shouldReturnBatchesByTenantAndProduct() throws Exception {
        InventoryBatchResponse response =
                InventoryBatchResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .productId(10L)
                        .batchCode("BATCH-001")
                        .receivedQuantity(20)
                        .build();

        when(inventoryBatchService
                .getBatchesByTenantAndProduct(1L, 10L))
                .thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/inventory/batches")
                                .param("tenantId", "1")
                                .param("productId", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(10));
    }

    @Test
    void shouldReturnBatchById() throws Exception {
        InventoryBatchResponse response =
                InventoryBatchResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .productId(10L)
                        .batchCode("BATCH-001")
                        .build();

        when(inventoryBatchService.getBatchById(1L, 1L))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/inventory/batches/1")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tenantId").value(1));
    }

    @Test
    void shouldReturn409WhenBatchAlreadyExists() throws Exception {
        CreateInventoryBatchRequest request =
                CreateInventoryBatchRequest.builder()
                        .tenantId(1L)
                        .productId(10L)
                        .batchCode("BATCH-001")
                        .receivedQuantity(20)
                        .receivedAt(LocalDate.of(2026, 10, 20))
                        .expiryDate(LocalDate.of(2028, 10, 20))
                        .build();

        when(inventoryBatchService.createBatch(
                any(CreateInventoryBatchRequest.class)
        )).thenThrow(
                new InventoryBatchAlreadyExistsException(
                        1L,
                        "BATCH-001"
                )
        );

        mockMvc.perform(
                        post("/api/inventory/batches")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturn400WhenBatchDateIsInvalid() throws Exception {
        CreateInventoryBatchRequest request =
                CreateInventoryBatchRequest.builder()
                        .tenantId(1L)
                        .productId(10L)
                        .batchCode("BATCH-002")
                        .receivedQuantity(20)
                        .receivedAt(LocalDate.of(2026, 10, 20))
                        .expiryDate(LocalDate.of(2026, 10, 19))
                        .build();

        when(inventoryBatchService.createBatch(
                any(CreateInventoryBatchRequest.class)
        )).thenThrow(
                new InvalidInventoryBatchDateException(
                        LocalDate.of(2026, 10, 20),
                        LocalDate.of(2026, 10, 19)
                )
        );

        mockMvc.perform(
                        post("/api/inventory/batches")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturn404WhenBatchNotFound() throws Exception {
        when(inventoryBatchService.getBatchById(1L, 999L))
                .thenThrow(
                        new InventoryBatchNotFoundException(
                                999L,
                                1L
                        )
                );

        mockMvc.perform(
                        get("/api/inventory/batches/999")
                                .param("tenantId", "1")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturn400WhenCreateRequestIsInvalid() throws Exception {
        String requestBody = """
                {
                  "tenantId": 1,
                  "productId": 10,
                  "batchCode": "",
                  "receivedQuantity": 0,
                  "receivedAt": "2026-10-20"
                }
                """;

        mockMvc.perform(
                        post("/api/inventory/batches")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}