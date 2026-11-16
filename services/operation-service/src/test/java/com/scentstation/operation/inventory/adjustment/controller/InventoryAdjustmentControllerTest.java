package com.scentstation.operation.inventory.adjustment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.common.exception.OperationExceptionHandler;
import com.scentstation.operation.inventory.adjustment.dto.request.AdjustInventoryRequest;
import com.scentstation.operation.inventory.adjustment.dto.response.InventoryAdjustmentResponse;
import com.scentstation.operation.inventory.adjustment.service.InventoryAdjustmentService;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
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

@WebMvcTest(InventoryAdjustmentController.class)
@Import(OperationExceptionHandler.class)
class InventoryAdjustmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryAdjustmentService service;

    @Test
    void shouldAdjustInventory() throws Exception {
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

        InventoryAdjustmentResponse response =
                InventoryAdjustmentResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .bottleId(10L)
                        .quantityBefore(
                                new BigDecimal("20.000")
                        )
                        .quantityAfter(
                                new BigDecimal("17.000")
                        )
                        .difference(
                                new BigDecimal("-3.000")
                        )
                        .actorId(25L)
                        .reason("Sensor reconciliation")
                        .bottleStatus(BottleStatus.INSTALLED)
                        .build();

        when(service.adjust(
                any(AdjustInventoryRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/inventory/adjustments")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper
                                                .writeValueAsString(
                                                        request
                                                )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.bottleId")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.quantityAfter")
                                .value(17.000)
                )
                .andExpect(
                        jsonPath("$.difference")
                                .value(-3.000)
                );
    }

    @Test
    void shouldReturnBottleAdjustmentHistory()
            throws Exception {

        InventoryAdjustmentResponse response =
                InventoryAdjustmentResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .bottleId(10L)
                        .quantityBefore(
                                new BigDecimal("20.000")
                        )
                        .quantityAfter(
                                new BigDecimal("17.000")
                        )
                        .difference(
                                new BigDecimal("-3.000")
                        )
                        .actorId(25L)
                        .reason("Sensor reconciliation")
                        .build();

        when(service.getBottleAdjustmentHistory(
                1L,
                10L
        )).thenReturn(List.of(response));

        mockMvc.perform(
                        get(
                                "/api/inventory/adjustments/"
                                        + "bottles/10"
                        )
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(
                        jsonPath("$[0].bottleId")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$[0].difference")
                                .value(-3.000)
                );
    }

    @Test
    void shouldReturn400WhenReasonIsBlank()
            throws Exception {

        String body = """
                {
                  "tenantId": 1,
                  "bottleId": 10,
                  "newQuantity": 17.0,
                  "actorId": 25,
                  "reason": ""
                }
                """;

        mockMvc.perform(
                        post("/api/inventory/adjustments")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(body)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400ForNegativeQuantity()
            throws Exception {

        String body = """
                {
                  "tenantId": 1,
                  "bottleId": 10,
                  "newQuantity": -1,
                  "actorId": 25,
                  "reason": "Manual correction"
                }
                """;

        mockMvc.perform(
                        post("/api/inventory/adjustments")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(body)
                )
                .andExpect(status().isBadRequest());
    }
}