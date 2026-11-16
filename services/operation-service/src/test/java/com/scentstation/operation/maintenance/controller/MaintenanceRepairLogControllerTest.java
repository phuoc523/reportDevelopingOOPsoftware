package com.scentstation.operation.maintenance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.common.exception.OperationExceptionHandler;
import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceRepairLogRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceRepairLogResponse;
import com.scentstation.operation.maintenance.enums.MaintenanceRepairResult;
import com.scentstation.operation.maintenance.service.MaintenanceRepairLogService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MaintenanceRepairLogController.class)
@Import(OperationExceptionHandler.class)
class MaintenanceRepairLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MaintenanceRepairLogService maintenanceRepairLogService;

    @Test
    void shouldCreateRepairLog() throws Exception {
        CreateMaintenanceRepairLogRequest request =
                new CreateMaintenanceRepairLogRequest();

        request.setTenantId(1L);
        request.setDiagnosedByActorId(200L);
        request.setDiagnosticResult(
                "Pump pressure low"
        );
        request.setCorrectiveAction(
                "Replace seal"
        );
        request.setCost(
                new BigDecimal("100.00")
        );
        request.setRepairResult(
                MaintenanceRepairResult.SUCCESS
        );

        MaintenanceRepairLogResponse response =
                MaintenanceRepairLogResponse.builder()
                        .id(5L)
                        .tenantId(1L)
                        .ticketId(10L)
                        .diagnosedByActorId(200L)
                        .repairResult(
                                MaintenanceRepairResult.SUCCESS
                        )
                        .build();

        when(
                maintenanceRepairLogService
                        .createRepairLog(
                                eq(10L),
                                any(CreateMaintenanceRepairLogRequest.class)
                        )
        ).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/10/repair-logs"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper
                                                .writeValueAsString(request)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.id").value(5)
                )
                .andExpect(
                        jsonPath("$.repairResult")
                                .value("SUCCESS")
                );
    }

    @Test
    void shouldGetRepairLogs() throws Exception {
        MaintenanceRepairLogResponse response =
                MaintenanceRepairLogResponse.builder()
                        .id(5L)
                        .tenantId(1L)
                        .ticketId(10L)
                        .repairResult(
                                MaintenanceRepairResult.SUCCESS
                        )
                        .build();

        when(
                maintenanceRepairLogService
                        .getRepairLogs(
                                1L,
                                10L
                        )
        ).thenReturn(List.of(response));

        mockMvc.perform(
                        get(
                                "/api/maintenance/tickets/10/repair-logs"
                        )
                                .param(
                                        "tenantId",
                                        "1"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[0].id").value(5)
                )
                .andExpect(
                        jsonPath("$[0].ticketId").value(10)
                );
    }

    @Test
    void shouldReturn400WhenRepairRequestInvalid()
            throws Exception {

        String body = """
                {
                  "tenantId": 1,
                  "diagnosedByActorId": 200,
                  "diagnosticResult": "",
                  "correctiveAction": "",
                  "cost": -1
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/10/repair-logs"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status").value(400)
                );
    }
}