package com.scentstation.operation.inventory.bottle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.inventory.batch.exception.OperationExceptionHandler;
import com.scentstation.operation.inventory.bottle.dto.request.CreateBottleRequest;
import com.scentstation.operation.inventory.bottle.dto.response.BottleResponse;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.exception.BottleAlreadyExistsException;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.service.BottleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BottleController.class)
@Import(OperationExceptionHandler.class)
class BottleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BottleService bottleService;

    @Test
    void shouldCreateBottle() throws Exception {
        CreateBottleRequest request =
                CreateBottleRequest.builder()
                        .tenantId(1L)
                        .productId(10L)
                        .batchId(5L)
                        .bottleCode("BTL-001")
                        .initialQuantity(new BigDecimal("100.000"))
                        .build();

        BottleResponse response =
                BottleResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .productId(10L)
                        .batchId(5L)
                        .bottleCode("BTL-001")
                        .initialQuantity(new BigDecimal("100.000"))
                        .currentQuantity(new BigDecimal("100.000"))
                        .status(BottleStatus.AVAILABLE)
                        .build();

        when(bottleService.createBottle(
                any(CreateBottleRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/inventory/bottles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.bottleCode").value("BTL-001"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.currentQuantity").value(100.000));
    }

    @Test
    void shouldReturnBottlesByTenant() throws Exception {
        BottleResponse response =
                BottleResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .productId(10L)
                        .batchId(5L)
                        .bottleCode("BTL-001")
                        .status(BottleStatus.AVAILABLE)
                        .build();

        when(bottleService.getBottlesByTenant(1L))
                .thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/inventory/bottles")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].bottleCode").value("BTL-001"));
    }

    @Test
    void shouldReturnBottlesByBatch() throws Exception {
        BottleResponse response =
                BottleResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .productId(10L)
                        .batchId(5L)
                        .bottleCode("BTL-001")
                        .build();

        when(bottleService.getBottlesByTenantAndBatch(
                1L,
                5L
        )).thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/inventory/bottles")
                                .param("tenantId", "1")
                                .param("batchId", "5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].batchId").value(5));
    }

    @Test
    void shouldReturnBottlesByProduct() throws Exception {
        BottleResponse response =
                BottleResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .productId(10L)
                        .batchId(5L)
                        .bottleCode("BTL-001")
                        .build();

        when(bottleService.getBottlesByTenantAndProduct(
                1L,
                10L
        )).thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/inventory/bottles")
                                .param("tenantId", "1")
                                .param("productId", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(10));
    }

    @Test
    void shouldReturnBottleById() throws Exception {
        BottleResponse response =
                BottleResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .productId(10L)
                        .batchId(5L)
                        .bottleCode("BTL-001")
                        .build();

        when(bottleService.getBottleById(1L, 1L))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/inventory/bottles/1")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tenantId").value(1));
    }

    @Test
    void shouldReturn409WhenBottleAlreadyExists() throws Exception {
        CreateBottleRequest request =
                CreateBottleRequest.builder()
                        .tenantId(1L)
                        .productId(10L)
                        .batchId(5L)
                        .bottleCode("BTL-001")
                        .initialQuantity(new BigDecimal("100.000"))
                        .build();

        when(bottleService.createBottle(
                any(CreateBottleRequest.class)
        )).thenThrow(
                new BottleAlreadyExistsException(
                        1L,
                        "BTL-001"
                )
        );

        mockMvc.perform(
                        post("/api/inventory/bottles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturn404WhenBottleNotFound() throws Exception {
        when(bottleService.getBottleById(
                1L,
                999L
        )).thenThrow(
                new BottleNotFoundException(
                        999L,
                        1L
                )
        );

        mockMvc.perform(
                        get("/api/inventory/bottles/999")
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
                  "batchId": 5,
                  "bottleCode": "",
                  "initialQuantity": 0
                }
                """;

        mockMvc.perform(
                        post("/api/inventory/bottles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}