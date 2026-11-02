package com.scentstation.operation.maintenance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.common.exception.OperationExceptionHandler;
import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.UpdateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceTicketResponse;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.exception.MaintenanceNotFoundException;
import com.scentstation.operation.maintenance.service.MaintenanceTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MaintenanceTicketController.class)
@Import(OperationExceptionHandler.class)
class MaintenanceTicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MaintenanceTicketService maintenanceTicketService;

    @Test
    void shouldCreateMaintenanceTicket() throws Exception {
        CreateMaintenanceTicketRequest request =
                new CreateMaintenanceTicketRequest();

        request.setTenantId(1L);
        request.setMachineId(10L);
        request.setLocationId(20L);
        request.setTitle("Pump failure");
        request.setDescription("Machine cannot spray");
        request.setCategory("PUMP_FAILURE");
        request.setSeverity(MaintenanceSeverity.MAJOR);
        request.setPriority(MaintenancePriority.HIGH);
        request.setReportedByActorId(100L);

        MaintenanceTicketResponse response =
                MaintenanceTicketResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .machineId(10L)
                        .locationId(20L)
                        .title("Pump failure")
                        .status(MaintenanceStatus.OPEN)
                        .build();

        when(
                maintenanceTicketService.createTicket(
                        any(CreateMaintenanceTicketRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post("/api/maintenance/tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tenantId").value(1))
                .andExpect(jsonPath("$.machineId").value(10))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void shouldReturnTicketsByTenant() throws Exception {
        MaintenanceTicketResponse response =
                MaintenanceTicketResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .machineId(10L)
                        .status(MaintenanceStatus.OPEN)
                        .build();

        when(
                maintenanceTicketService.getTickets(1L)
        ).thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/maintenance/tickets")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].tenantId").value(1));
    }

    @Test
    void shouldFilterTicketsByStatus() throws Exception {
        MaintenanceTicketResponse response =
                MaintenanceTicketResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .status(MaintenanceStatus.OPEN)
                        .build();

        when(
                maintenanceTicketService.getTicketsByStatus(
                        1L,
                        MaintenanceStatus.OPEN
                )
        ).thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/maintenance/tickets")
                                .param("tenantId", "1")
                                .param("status", "OPEN")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void shouldReturnTicketById() throws Exception {
        MaintenanceTicketResponse response =
                MaintenanceTicketResponse.builder()
                        .id(5L)
                        .tenantId(1L)
                        .machineId(10L)
                        .status(MaintenanceStatus.OPEN)
                        .build();

        when(
                maintenanceTicketService.getTicketById(
                        1L,
                        5L
                )
        ).thenReturn(response);

        mockMvc.perform(
                        get("/api/maintenance/tickets/5")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void shouldUpdateMaintenanceTicket() throws Exception {
        UpdateMaintenanceTicketRequest request =
                new UpdateMaintenanceTicketRequest();

        request.setTitle("Updated pump failure");
        request.setDescription("Pump requires inspection");
        request.setCategory("PUMP_FAILURE");
        request.setSeverity(MaintenanceSeverity.CRITICAL);
        request.setPriority(MaintenancePriority.URGENT);

        MaintenanceTicketResponse response =
                MaintenanceTicketResponse.builder()
                        .id(5L)
                        .tenantId(1L)
                        .title("Updated pump failure")
                        .severity(MaintenanceSeverity.CRITICAL)
                        .priority(MaintenancePriority.URGENT)
                        .status(MaintenanceStatus.OPEN)
                        .build();

        when(
                maintenanceTicketService.updateTicket(
                        any(),
                        any(),
                        any(UpdateMaintenanceTicketRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        put("/api/maintenance/tickets/5")
                                .param("tenantId", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
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
    void shouldReturn404WhenTicketDoesNotExist() throws Exception {
        when(
                maintenanceTicketService.getTicketById(
                        1L,
                        999L
                )
        ).thenThrow(
                new MaintenanceNotFoundException(999L)
        );

        mockMvc.perform(
                        get("/api/maintenance/tickets/999")
                                .param("tenantId", "1")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturn400ForInvalidCreateRequest() throws Exception {
        String body = """
                {
                  "tenantId": 1,
                  "machineId": 10,
                  "title": "",
                  "description": "",
                  "category": "",
                  "reportedByActorId": 100
                }
                """;

        mockMvc.perform(
                        post("/api/maintenance/tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}