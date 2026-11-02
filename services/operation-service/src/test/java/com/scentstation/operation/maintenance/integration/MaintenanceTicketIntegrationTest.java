package com.scentstation.operation.maintenance.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MaintenanceTicketIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MaintenanceTicketRepository maintenanceTicketRepository;

    @BeforeEach
    void setUp() {
        maintenanceTicketRepository.deleteAll();
    }

    @Test
    void shouldCreateAndReadMaintenanceTicket() throws Exception {
        String body = """
                {
                  "tenantId": 1,
                  "machineId": 100,
                  "locationId": 50,
                  "title": "Pump failure",
                  "description": "Pump cannot dispense fragrance",
                  "category": "PUMP_FAILURE",
                  "severity": "MAJOR",
                  "priority": "HIGH",
                  "reportedByActorId": 900
                }
                """;

        mockMvc.perform(
                        post("/api/maintenance/tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(1))
                .andExpect(jsonPath("$.machineId").value(100))
                .andExpect(jsonPath("$.status").value("OPEN"));

        MaintenanceTicket saved =
                maintenanceTicketRepository
                        .findAllByTenantId(1L)
                        .get(0);

        mockMvc.perform(
                        get(
                                "/api/maintenance/tickets/{id}",
                                saved.getId()
                        )
                                .param(
                                        "tenantId",
                                        "1"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.title")
                                .value("Pump failure")
                );
    }

    @Test
    void shouldHideTicketFromAnotherTenant() throws Exception {
        MaintenanceTicket ticket =
                MaintenanceTicket.builder()
                        .tenantId(2L)
                        .machineId(100L)
                        .title("Tenant 2 ticket")
                        .description("Maintenance")
                        .category("TEST")
                        .severity(
                                MaintenanceSeverity.MAJOR
                        )
                        .priority(
                                MaintenancePriority.HIGH
                        )
                        .status(
                                MaintenanceStatus.OPEN
                        )
                        .reportedByActorId(900L)
                        .build();

        MaintenanceTicket saved =
                maintenanceTicketRepository.save(ticket);

        mockMvc.perform(
                        get(
                                "/api/maintenance/tickets/{id}",
                                saved.getId()
                        )
                                .param(
                                        "tenantId",
                                        "1"
                                )
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateTicketWithoutChangingWorkflowState()
            throws Exception {

        MaintenanceTicket ticket =
                MaintenanceTicket.builder()
                        .tenantId(1L)
                        .machineId(100L)
                        .title("Initial")
                        .description("Initial description")
                        .category("TEST")
                        .severity(
                                MaintenanceSeverity.MINOR
                        )
                        .priority(
                                MaintenancePriority.LOW
                        )
                        .status(
                                MaintenanceStatus.OPEN
                        )
                        .reportedByActorId(900L)
                        .build();

        MaintenanceTicket saved =
                maintenanceTicketRepository.save(ticket);

        String body = """
                {
                  "title": "Updated",
                  "description": "Updated maintenance description",
                  "category": "PUMP_FAILURE",
                  "severity": "CRITICAL",
                  "priority": "URGENT"
                }
                """;

        mockMvc.perform(
                        put(
                                "/api/maintenance/tickets/{id}",
                                saved.getId()
                        )
                                .param(
                                        "tenantId",
                                        "1"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(body)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.title")
                                .value("Updated")
                )
                .andExpect(
                        jsonPath("$.severity")
                                .value("CRITICAL")
                )
                .andExpect(
                        jsonPath("$.priority")
                                .value("URGENT")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("OPEN")
                );
    }
}