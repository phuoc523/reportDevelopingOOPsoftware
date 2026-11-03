package com.scentstation.operation.maintenance.controller;

import com.scentstation.operation.common.exception.OperationExceptionHandler;
import com.scentstation.operation.maintenance.dto.response.MaintenanceHistoryResponse;
import com.scentstation.operation.maintenance.dto.response.MaintenanceMachineHistoryResponse;
import com.scentstation.operation.maintenance.dto.response.MaintenanceTicketResponse;
import com.scentstation.operation.maintenance.exception.MaintenanceNotFoundException;
import com.scentstation.operation.maintenance.service.MaintenanceHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MaintenanceHistoryController.class)
@Import(OperationExceptionHandler.class)
class MaintenanceHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MaintenanceHistoryService service;

    @Test
    void shouldReturnTicketHistory() throws Exception {
        when(service.getTicketHistory(1L, 10L)).thenReturn(
                MaintenanceHistoryResponse.builder()
                        .ticket(MaintenanceTicketResponse.builder().id(10L).build())
                        .assignmentHistory(List.of())
                        .checklist(List.of())
                        .repairLogs(List.of())
                        .postMaintenanceTests(List.of())
                        .build()
        );

        mockMvc.perform(get("/api/maintenance/tickets/10/history")
                        .param("tenantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.id").value(10))
                .andExpect(jsonPath("$.checklist").isArray());
    }

    @Test
    void shouldReturnMachineHistory() throws Exception {
        when(service.getMachineHistory(1L, 100L)).thenReturn(
                MaintenanceMachineHistoryResponse.builder()
                        .tenantId(1L).machineId(100L)
                        .tickets(List.of(MaintenanceTicketResponse.builder().id(10L).build()))
                        .build()
        );

        mockMvc.perform(get("/api/maintenance/machines/100/history")
                        .param("tenantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machineId").value(100))
                .andExpect(jsonPath("$.tickets[0].id").value(10));
    }

    @Test
    void shouldReturn404ForCrossTenantTicketHistory() throws Exception {
        when(service.getTicketHistory(2L, 10L))
                .thenThrow(new MaintenanceNotFoundException(10L));

        mockMvc.perform(get("/api/maintenance/tickets/10/history")
                        .param("tenantId", "2"))
                .andExpect(status().isNotFound());
    }
}
