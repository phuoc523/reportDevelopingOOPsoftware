package com.scentstation.operation.inventory.estimation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.inventory.batch.exception.OperationExceptionHandler;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.estimation.dto.request.UpdateSprayCalibrationRequest;
import com.scentstation.operation.inventory.estimation.dto.response.RemainingSprayResponse;
import com.scentstation.operation.inventory.estimation.exception.SprayCalibrationNotFoundException;
import com.scentstation.operation.inventory.estimation.service.RemainingSprayService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RemainingSprayController.class)
@Import(OperationExceptionHandler.class)
class RemainingSprayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RemainingSprayService remainingSprayService;

    @Test
    void shouldUpdateCalibration() throws Exception {
        UpdateSprayCalibrationRequest request =
                UpdateSprayCalibrationRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .consumptionPerSpray(
                                new BigDecimal("0.200")
                        )
                        .build();

        RemainingSprayResponse response =
                RemainingSprayResponse.builder()
                        .bottleId(10L)
                        .tenantId(1L)
                        .currentQuantity(
                                new BigDecimal("20.000")
                        )
                        .calibratedConsumptionPerSpray(
                                new BigDecimal("0.200")
                        )
                        .estimatedRemainingSprays(100L)
                        .successfulSprayCount(200L)
                        .bottleStatus(BottleStatus.INSTALLED)
                        .build();

        when(remainingSprayService.updateCalibration(
                any(UpdateSprayCalibrationRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        put("/api/inventory/estimations/calibration")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bottleId").value(10))
                .andExpect(
                        jsonPath("$.estimatedRemainingSprays")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.calibratedConsumptionPerSpray")
                                .value(0.200)
                );
    }

    @Test
    void shouldGetRemainingSprays() throws Exception {
        RemainingSprayResponse response =
                RemainingSprayResponse.builder()
                        .bottleId(10L)
                        .tenantId(1L)
                        .currentQuantity(
                                new BigDecimal("19.800")
                        )
                        .calibratedConsumptionPerSpray(
                                new BigDecimal("0.200")
                        )
                        .estimatedRemainingSprays(99L)
                        .successfulSprayCount(245L)
                        .bottleStatus(BottleStatus.INSTALLED)
                        .build();

        when(remainingSprayService.getRemainingSprays(
                1L,
                10L
        )).thenReturn(response);

        mockMvc.perform(
                        get(
                                "/api/inventory/estimations/"
                                        + "bottles/10/remaining-sprays"
                        )
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bottleId").value(10))
                .andExpect(
                        jsonPath("$.estimatedRemainingSprays")
                                .value(99)
                )
                .andExpect(
                        jsonPath("$.successfulSprayCount")
                                .value(245)
                );
    }

    @Test
    void shouldReturn400WhenCalibrationIsMissing()
            throws Exception {

        when(remainingSprayService.getRemainingSprays(
                1L,
                10L
        )).thenThrow(
                new SprayCalibrationNotFoundException(10L)
        );

        mockMvc.perform(
                        get(
                                "/api/inventory/estimations/"
                                        + "bottles/10/remaining-sprays"
                        )
                                .param("tenantId", "1")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturn400WhenCalibrationValueIsInvalid()
            throws Exception {

        String requestBody = """
                {
                  "tenantId": 1,
                  "bottleId": 10,
                  "consumptionPerSpray": 0
                }
                """;

        mockMvc.perform(
                        put("/api/inventory/estimations/calibration")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturn400WhenBottleIdIsInvalid()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/inventory/estimations/"
                                        + "bottles/0/remaining-sprays"
                        )
                                .param("tenantId", "1")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}