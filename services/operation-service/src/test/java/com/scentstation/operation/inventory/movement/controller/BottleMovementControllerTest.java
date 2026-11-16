package com.scentstation.operation.inventory.movement.controller;

import com.scentstation.operation.common.exception.OperationExceptionHandler;
import com.scentstation.operation.inventory.movement.dto.response.BottleMovementResponse;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import com.scentstation.operation.inventory.movement.service.BottleMovementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BottleMovementController.class)
@Import(OperationExceptionHandler.class)
class BottleMovementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BottleMovementService movementService;

    @Test
    void shouldReturnTenantMovementHistory()
            throws Exception {

        BottleMovementResponse response =
                BottleMovementResponse.builder()
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
                        .difference(
                                new BigDecimal("-3.000")
                        )
                        .build();

        when(movementService.getTenantHistory(1L))
                .thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/inventory/movements")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(
                        jsonPath("$[0].movementType")
                                .value("ADJUSTMENT")
                );
    }

    @Test
    void shouldFilterMovementHistoryByType()
            throws Exception {

        when(movementService.getHistoryByType(
                1L,
                BottleMovementType.REFILL
        )).thenReturn(List.of());

        mockMvc.perform(
                        get("/api/inventory/movements")
                                .param("tenantId", "1")
                                .param("type", "REFILL")
                )
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnBottleMovementHistory()
            throws Exception {

        when(movementService.getBottleHistory(
                1L,
                10L
        )).thenReturn(List.of());

        mockMvc.perform(
                        get(
                                "/api/inventory/movements/"
                                        + "bottles/10"
                        )
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnMachineMovementHistory()
            throws Exception {

        when(movementService.getMachineHistory(
                1L,
                3L
        )).thenReturn(List.of());

        mockMvc.perform(
                        get(
                                "/api/inventory/movements/"
                                        + "machines/3"
                        )
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn400ForInvalidTenantId()
            throws Exception {

        mockMvc.perform(
                        get("/api/inventory/movements")
                                .param("tenantId", "0")
                )
                .andExpect(status().isBadRequest());
    }
}