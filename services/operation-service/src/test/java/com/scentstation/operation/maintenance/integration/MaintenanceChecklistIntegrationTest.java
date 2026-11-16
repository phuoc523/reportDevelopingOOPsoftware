package com.scentstation.operation.maintenance.integration;

import com.scentstation.operation.maintenance.entity.MaintenanceChecklistItem;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.repository.MaintenanceChecklistRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MaintenanceChecklistIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MaintenanceTicketRepository maintenanceTicketRepository;

    @Autowired
    private MaintenanceChecklistRepository maintenanceChecklistRepository;

    private MaintenanceTicket ticket;

    @BeforeEach
    void setUp() {
        maintenanceChecklistRepository.deleteAll();
        maintenanceTicketRepository.deleteAll();

        ticket = maintenanceTicketRepository.save(
                MaintenanceTicket.builder()
                        .tenantId(1L)
                        .machineId(100L)
                        .title("Pump failure")
                        .description("Pump inspection")
                        .category("PUMP_FAILURE")
                        .severity(MaintenanceSeverity.MAJOR)
                        .priority(MaintenancePriority.HIGH)
                        .status(MaintenanceStatus.IN_PROGRESS)
                        .reportedByActorId(100L)
                        .assignedToActorId(200L)
                        .build()
        );
    }

    @Test
    void shouldCreateAndCompleteRequiredChecklist()
            throws Exception {

        String createBody = """
                {
                  "tenantId": 1,
                  "itemName": "Check pump pressure",
                  "description": "Measure pump pressure",
                  "required": true
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/{id}/checklist",
                                ticket.getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.required").value(true)
                )
                .andExpect(
                        jsonPath("$.completed").value(false)
                );

        MaintenanceChecklistItem item =
                maintenanceChecklistRepository
                        .findAllByTenantIdAndTicketIdOrderByIdAsc(
                                1L,
                                ticket.getId()
                        )
                        .get(0);

        assertTrue(
                maintenanceChecklistRepository
                        .existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(
                                1L,
                                ticket.getId()
                        )
        );

        String completeBody = """
                {
                  "tenantId": 1,
                  "completedByActorId": 200,
                  "resultNote": "Pressure is normal"
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/{ticketId}/checklist/{itemId}/complete",
                                ticket.getId(),
                                item.getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(completeBody)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.completed").value(true)
                );

        assertFalse(
                maintenanceChecklistRepository
                        .existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(
                                1L,
                                ticket.getId()
                        )
        );
    }

    @Test
    void shouldRejectChecklistItemFromDifferentTicket()
            throws Exception {

        MaintenanceTicket anotherTicket =
                maintenanceTicketRepository.save(
                        MaintenanceTicket.builder()
                                .tenantId(1L)
                                .machineId(200L)
                                .title("Another ticket")
                                .description("Another issue")
                                .category("TEST")
                                .severity(MaintenanceSeverity.MINOR)
                                .priority(MaintenancePriority.LOW)
                                .status(MaintenanceStatus.IN_PROGRESS)
                                .reportedByActorId(100L)
                                .build()
                );

        MaintenanceChecklistItem item =
                maintenanceChecklistRepository.save(
                        MaintenanceChecklistItem.builder()
                                .tenantId(1L)
                                .ticketId(
                                        anotherTicket.getId()
                                )
                                .itemName("Other checklist")
                                .required(true)
                                .completed(false)
                                .build()
                );

        String body = """
                {
                  "tenantId": 1,
                  "completedByActorId": 200
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/{ticketId}/checklist/{itemId}/complete",
                                ticket.getId(),
                                item.getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isNotFound());
    }
}