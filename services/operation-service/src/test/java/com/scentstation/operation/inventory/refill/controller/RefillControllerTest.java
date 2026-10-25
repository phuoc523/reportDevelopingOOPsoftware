package com.scentstation.operation.inventory.refill.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.inventory.batch.exception.OperationExceptionHandler;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.refill.dto.request.RefillBottleRequest;
import com.scentstation.operation.inventory.refill.dto.response.RefillBottleResponse;
import com.scentstation.operation.inventory.refill.exception.BottleNotRefillableException;
import com.scentstation.operation.inventory.refill.service.RefillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RefillController.class)
@Import(OperationExceptionHandler.class)
class RefillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RefillService refillService;

    @Test
    void shouldRefillBottle() throws Exception {
        RefillBottleRequest request =
                RefillBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .refillQuantity(new BigDecimal("50.000"))
                        .actorId(5L)
                        .reason("Routine refill")
                        .build();

        RefillBottleResponse response =
                RefillBottleResponse.builder()
                        .bottleId(10L)
                        .tenantId(1L)
                        .quantityBefore(new BigDecimal("20.000"))
                        .refillQuantity(new BigDecimal("50.000"))
                        .quantityAfter(new BigDecimal("70.000"))
                        .status(BottleStatus.AVAILABLE)
                        .actorId(5L)
                        .reason("Routine refill")
                        .build();

        when(refillService.refillBottle(
                any(RefillBottleRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/inventory/refills")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bottleId").value(10))
                .andExpect(jsonPath("$.quantityBefore").value(20.000))
                .andExpect(jsonPath("$.refillQuantity").value(50.000))
                .andExpect(jsonPath("$.quantityAfter").value(70.000))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void shouldReturn400WhenBottleCannotBeRefilled() throws Exception {
        RefillBottleRequest request =
                RefillBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .refillQuantity(new BigDecimal("50.000"))
                        .actorId(5L)
                        .build();

        when(refillService.refillBottle(
                any(RefillBottleRequest.class)
        )).thenThrow(
                new BottleNotRefillableException(
                        10L,
                        "DAMAGED"
                )
        );

        mockMvc.perform(
                        post("/api/inventory/refills")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturn400WhenRefillQuantityIsInvalid() throws Exception {
        String requestBody = """
                {
                  "tenantId": 1,
                  "bottleId": 10,
                  "refillQuantity": 0,
                  "actorId": 5
                }
                """;

        mockMvc.perform(
                        post("/api/inventory/refills")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturn400WhenActorIdIsMissing() throws Exception {
        String requestBody = """
                {
                  "tenantId": 1,
                  "bottleId": 10,
                  "refillQuantity": 50.000
                }
                """;

        mockMvc.perform(
                        post("/api/inventory/refills")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}