package com.scentstation.operation.inventory.reconciliation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.common.exception.OperationExceptionHandler;
import com.scentstation.operation.inventory.reconciliation.dto.request.RecordSensorReadingRequest;
import com.scentstation.operation.inventory.reconciliation.dto.response.SensorReconciliationResponse;
import com.scentstation.operation.inventory.reconciliation.enums.ReconciliationStatus;
import com.scentstation.operation.inventory.reconciliation.exception.SensorInstallationMismatchException;
import com.scentstation.operation.inventory.reconciliation.service.SensorReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SensorReconciliationController.class)
@Import(OperationExceptionHandler.class)
class SensorReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SensorReconciliationService sensorReconciliationService;

    @Test
    void shouldReconcileSensorReadingSuccessfully()
            throws Exception {

        RecordSensorReadingRequest request =
                RecordSensorReadingRequest.builder()
                        .tenantId(1L)
                        .sensorEventId("SENSOR-001")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .sensorQuantity(
                                new BigDecimal("17.000")
                        )
                        .measuredAt(
                                LocalDateTime.of(
                                        2026,
                                        10,
                                        26,
                                        23,
                                        30
                                )
                        )
                        .build();

        SensorReconciliationResponse response =
                SensorReconciliationResponse.builder()
                        .id(1L)
                        .sensorEventId("SENSOR-001")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .predictedQuantity(
                                new BigDecimal("20.000")
                        )
                        .sensorQuantity(
                                new BigDecimal("17.000")
                        )
                        .discrepancy(
                                new BigDecimal("3.000")
                        )
                        .status(
                                ReconciliationStatus.DISCREPANCY
                        )
                        .lowStock(false)
                        .estimatedRemainingSprays(85L)
                        .measuredAt(request.getMeasuredAt())
                        .build();

        when(sensorReconciliationService.reconcile(
                any(RecordSensorReadingRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/inventory/reconciliations")
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
                        jsonPath("$.sensorEventId")
                                .value("SENSOR-001")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("DISCREPANCY")
                )
                .andExpect(
                        jsonPath("$.lowStock")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.estimatedRemainingSprays")
                                .value(85)
                );
    }

    @Test
    void shouldReturn409WhenInstallationDoesNotMatch()
            throws Exception {

        RecordSensorReadingRequest request =
                RecordSensorReadingRequest.builder()
                        .tenantId(1L)
                        .sensorEventId("SENSOR-002")
                        .bottleId(10L)
                        .machineId(99L)
                        .slotId(2L)
                        .sensorQuantity(
                                new BigDecimal("17.000")
                        )
                        .measuredAt(LocalDateTime.now())
                        .build();

        when(sensorReconciliationService.reconcile(
                any(RecordSensorReadingRequest.class)
        )).thenThrow(
                new SensorInstallationMismatchException(
                        10L,
                        99L,
                        2L
                )
        );

        mockMvc.perform(
                        post("/api/inventory/reconciliations")
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
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                );
    }

    @Test
    void shouldReturn400WhenSensorQuantityIsNegative()
            throws Exception {

        String requestBody = """
                {
                  "tenantId": 1,
                  "sensorEventId": "SENSOR-003",
                  "bottleId": 10,
                  "machineId": 3,
                  "slotId": 2,
                  "sensorQuantity": -1.0,
                  "measuredAt": "2026-10-26T23:30:00"
                }
                """;

        mockMvc.perform(
                        post("/api/inventory/reconciliations")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                );
    }

    @Test
    void shouldReturn400WhenSensorEventIdIsBlank()
            throws Exception {

        String requestBody = """
                {
                  "tenantId": 1,
                  "sensorEventId": "",
                  "bottleId": 10,
                  "machineId": 3,
                  "slotId": 2,
                  "sensorQuantity": 10.0,
                  "measuredAt": "2026-10-26T23:30:00"
                }
                """;

        mockMvc.perform(
                        post("/api/inventory/reconciliations")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                );
    }
}