package com.scentstation.operation.maintenance.integration;

import com.scentstation.operation.maintenance.entity.MaintenanceAssignmentHistory;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.repository.MaintenanceAssignmentHistoryRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MaintenanceAssignmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MaintenanceTicketRepository maintenanceTicketRepository;

    @Autowired
    private MaintenanceAssignmentHistoryRepository
            assignmentHistoryRepository;

    private MaintenanceTicket ticket;

    @BeforeEach
    void setUp() {
        assignmentHistoryRepository.deleteAll();
        maintenanceTicketRepository.deleteAll();

        ticket = maintenanceTicketRepository.save(
                MaintenanceTicket.builder()
                        .tenantId(1L)
                        .machineId(10L)
                        .title("Pump failure")
                        .description("Pump cannot spray")
                        .category("PUMP_FAILURE")
                        .severity(
                                MaintenanceSeverity.MAJOR
                        )
                        .priority(
                                MaintenancePriority.HIGH
                        )
                        .status(
                                MaintenanceStatus.OPEN
                        )
                        .reportedByActorId(100L)
                        .build()
        );
    }

    @Test
    void shouldAssignAndAcknowledgeTechnician()
            throws Exception {

        String assignBody = """
                {
                  "tenantId": 1,
                  "technicianActorId": 200,
                  "assignedByActorId": 100
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/{id}/assignment",
                                ticket.getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(assignBody)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.ticketStatus")
                                .value("ASSIGNED")
                );

        MaintenanceTicket assigned =
                maintenanceTicketRepository
                        .findById(ticket.getId())
                        .orElseThrow();

        assertEquals(
                200L,
                assigned.getAssignedToActorId()
        );

        assertEquals(
                MaintenanceStatus.ASSIGNED,
                assigned.getStatus()
        );

        String acknowledgeBody = """
                {
                  "tenantId": 1,
                  "technicianActorId": 200
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/{id}/assignment/acknowledge",
                                ticket.getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(acknowledgeBody)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.ticketStatus")
                                .value("IN_PROGRESS")
                );

        MaintenanceTicket acknowledged =
                maintenanceTicketRepository
                        .findById(ticket.getId())
                        .orElseThrow();

        assertEquals(
                MaintenanceStatus.IN_PROGRESS,
                acknowledged.getStatus()
        );

        List<MaintenanceAssignmentHistory> history =
                assignmentHistoryRepository
                        .findAllByTenantIdAndTicketIdOrderByCreatedAtAsc(
                                1L,
                                ticket.getId()
                        );

        assertEquals(2, history.size());

        assertEquals(
                "ASSIGNED",
                history.get(0)
                        .getEventType()
                        .name()
        );

        assertEquals(
                "ACKNOWLEDGED",
                history.get(1)
                        .getEventType()
                        .name()
        );
    }

    @Test
    void shouldReassignTechnicianAndPreserveHistory()
            throws Exception {

        String firstAssignment = """
                {
                  "tenantId": 1,
                  "technicianActorId": 200,
                  "assignedByActorId": 100
                }
                """;

        mockMvc.perform(
                post(
                        "/api/maintenance/tickets/{id}/assignment",
                        ticket.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstAssignment)
        ).andExpect(status().isOk());

        String reassignment = """
                {
                  "tenantId": 1,
                  "technicianActorId": 300,
                  "assignedByActorId": 101
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/{id}/assignment",
                                ticket.getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reassignment)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.eventType")
                                .value("REASSIGNED")
                );

        List<MaintenanceAssignmentHistory> history =
                assignmentHistoryRepository
                        .findAllByTenantIdAndTicketIdOrderByCreatedAtAsc(
                                1L,
                                ticket.getId()
                        );

        assertEquals(2, history.size());

        assertEquals(
                200L,
                history.get(1)
                        .getPreviousTechnicianActorId()
        );

        assertEquals(
                300L,
                history.get(1)
                        .getTechnicianActorId()
        );
    }
}