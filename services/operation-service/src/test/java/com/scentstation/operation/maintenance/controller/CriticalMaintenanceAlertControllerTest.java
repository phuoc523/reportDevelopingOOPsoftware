package com.scentstation.operation.maintenance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.common.exception.OperationExceptionHandler;
import com.scentstation.operation.maintenance.dto.request.CriticalMaintenanceAlertRequest;
import com.scentstation.operation.maintenance.dto.response.CriticalMaintenanceAlertResponse;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.exception.CriticalAlertAlreadyProcessedException;
import com.scentstation.operation.maintenance.service.CriticalMaintenanceAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CriticalMaintenanceAlertController.class)
@Import(OperationExceptionHandler.class)
class CriticalMaintenanceAlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CriticalMaintenanceAlertService
            criticalMaintenanceAlertService;

    @Test
    void shouldProcessCriticalAlert() throws Exception {
        CriticalMaintenanceAlertRequest request =
                new CriticalMaintenanceAlertRequest();

        request.setTenantId(1L);
        request.setMachineId(10L);
        request.setAlertId("ALERT-001");
        request.setAlertType("PUMP_FAILURE");
        request.setMessage("Critical pump failure");
        request.setOccurredAt(LocalDateTime.now());

        CriticalMaintenanceAlertResponse response =
                CriticalMaintenanceAlertResponse.builder()
                        .alertId("ALERT-001")
                        .ticketId(100L)
                        .tenantId(1L)
                        .machineId(10L)
                        .severity(
                                MaintenanceSeverity.CRITICAL
                        )
                        .priority(
                                MaintenancePriority.URGENT
                        )
                        .status(
                                MaintenanceStatus.OPEN
                        )
                        .build();

        when(
                criticalMaintenanceAlertService
                        .processCriticalAlert(
                                any(CriticalMaintenanceAlertRequest.class)
                        )
        ).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/maintenance/alerts/critical"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper
                                                .writeValueAsString(request)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.alertId")
                                .value("ALERT-001")
                )
                .andExpect(
                        jsonPath("$.ticketId")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.severity")
                                .value("CRITICAL")
                )
                .andExpect(
                        jsonPath("$.priority")
                                .value("URGENT")
                );
    }

    @Test
    void shouldReturn409WhenAlertAlreadyProcessed()
            throws Exception {

        CriticalMaintenanceAlertRequest request =
                new CriticalMaintenanceAlertRequest();

        request.setTenantId(1L);
        request.setMachineId(10L);
        request.setAlertId("ALERT-001");
        request.setAlertType("PUMP_FAILURE");
        request.setMessage("Critical pump failure");
        request.setOccurredAt(LocalDateTime.now());

        when(
                criticalMaintenanceAlertService
                        .processCriticalAlert(any())
        ).thenThrow(
                new CriticalAlertAlreadyProcessedException(
                        "ALERT-001"
                )
        );

        mockMvc.perform(
                        post(
                                "/api/maintenance/alerts/critical"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper
                                                .writeValueAsString(request)
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                );
    }

    @Test
    void shouldReturn400ForInvalidCriticalAlert()
            throws Exception {

        String body = """
                {
                  "tenantId": 1,
                  "machineId": 10,
                  "alertId": "",
                  "alertType": "",
                  "message": ""
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/maintenance/alerts/critical"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                );
    }

    @Test
    void shouldReturn400ForInvalidTenantAndMachine()
            throws Exception {
        String body = """
                {
                  "tenantId": 0,
                  "machineId": 0,
                  "alertId": "ALERT-001",
                  "alertType": "PUMP_FAILURE",
                  "message": "Critical pump failure",
                  "occurredAt": "2026-11-03T10:00:00"
                }
                """;

        mockMvc.perform(
                        post("/api/maintenance/alerts/critical")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
