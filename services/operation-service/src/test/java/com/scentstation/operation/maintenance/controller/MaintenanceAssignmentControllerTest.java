package com.scentstation.operation.maintenance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.common.exception.OperationExceptionHandler;
import com.scentstation.operation.maintenance.dto.request.AssignMaintenanceRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceAssignmentResponse;
import com.scentstation.operation.maintenance.enums.MaintenanceAssignmentEventType;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.service.MaintenanceAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MaintenanceAssignmentController.class)
@Import(OperationExceptionHandler.class)
class MaintenanceAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MaintenanceAssignmentService maintenanceAssignmentService;

    @Test
    void shouldAssignTechnician() throws Exception {
        AssignMaintenanceRequest request =
                new AssignMaintenanceRequest();

        request.setTenantId(1L);
        request.setTechnicianActorId(200L);
        request.setAssignedByActorId(100L);

        MaintenanceAssignmentResponse response =
                MaintenanceAssignmentResponse.builder()
                        .ticketId(10L)
                        .tenantId(1L)
                        .technicianActorId(200L)
                        .performedByActorId(100L)
                        .eventType(
                                MaintenanceAssignmentEventType.ASSIGNED
                        )
                        .ticketStatus(
                                MaintenanceStatus.ASSIGNED
                        )
                        .build();

        when(
                maintenanceAssignmentService
                        .assignTechnician(
                                eq(10L),
                                any(AssignMaintenanceRequest.class)
                        )
        ).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/10/assignment"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper
                                                .writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.technicianActorId")
                                .value(200)
                )
                .andExpect(
                        jsonPath("$.eventType")
                                .value("ASSIGNED")
                )
                .andExpect(
                        jsonPath("$.ticketStatus")
                                .value("ASSIGNED")
                );
    }

    @Test
    void shouldAcknowledgeAssignment() throws Exception {
        String body = """
                {
                  "tenantId": 1,
                  "technicianActorId": 200
                }
                """;

        MaintenanceAssignmentResponse response =
                MaintenanceAssignmentResponse.builder()
                        .ticketId(10L)
                        .tenantId(1L)
                        .technicianActorId(200L)
                        .performedByActorId(200L)
                        .eventType(
                                MaintenanceAssignmentEventType.ACKNOWLEDGED
                        )
                        .ticketStatus(
                                MaintenanceStatus.IN_PROGRESS
                        )
                        .build();

        when(
                maintenanceAssignmentService
                        .acknowledgeAssignment(
                                1L,
                                10L,
                                200L
                        )
        ).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/10/assignment/acknowledge"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.eventType")
                                .value("ACKNOWLEDGED")
                )
                .andExpect(
                        jsonPath("$.ticketStatus")
                                .value("IN_PROGRESS")
                );
    }

    @Test
    void shouldReturn400ForInvalidAssignmentRequest()
            throws Exception {

        String body = """
                {
                  "tenantId": 1,
                  "technicianActorId": 0,
                  "assignedByActorId": 100
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/10/assignment"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest());
    }
}