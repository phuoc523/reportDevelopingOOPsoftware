package com.scentstation.operation.maintenance.integration;

import com.scentstation.operation.maintenance.entity.MaintenanceRepairLog;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.repository.MaintenanceRepairLogRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MaintenanceRepairLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MaintenanceTicketRepository maintenanceTicketRepository;

    @Autowired
    private MaintenanceRepairLogRepository maintenanceRepairLogRepository;

    private MaintenanceTicket ticket;

    @BeforeEach
    void setUp() {
        maintenanceRepairLogRepository.deleteAll();
        maintenanceTicketRepository.deleteAll();

        ticket = maintenanceTicketRepository.save(
                MaintenanceTicket.builder()
                        .tenantId(1L)
                        .machineId(100L)
                        .title("Pump failure")
                        .description("Pump cannot spray")
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
    void shouldCreateAndReadRepairLog()
            throws Exception {

        String body = """
                {
                  "tenantId": 1,
                  "diagnosedByActorId": 200,
                  "diagnosticResult": "Pump seal damaged",
                  "correctiveAction": "Replace pump seal",
                  "replacedPart": "Pump seal",
                  "cost": 150.00,
                  "repairResult": "SUCCESS",
                  "note": "Pump operates normally",
                  "evidenceUrl": "/evidence/pump-seal.jpg"
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/{id}/repair-logs",
                                ticket.getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.repairResult")
                                .value("SUCCESS")
                );

        List<MaintenanceRepairLog> logs =
                maintenanceRepairLogRepository
                        .findAllByTenantIdAndTicketIdOrderByCreatedAtAsc(
                                1L,
                                ticket.getId()
                        );

        assertEquals(1, logs.size());

        assertEquals(
                "Pump seal",
                logs.get(0).getReplacedPart()
        );

        mockMvc.perform(
                        get(
                                "/api/maintenance/tickets/{id}/repair-logs",
                                ticket.getId()
                        )
                                .param(
                                        "tenantId",
                                        "1"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[0].repairResult")
                                .value("SUCCESS")
                );
    }

    @Test
    void shouldNotCreateRepairLogAcrossTenant()
            throws Exception {

        String body = """
                {
                  "tenantId": 2,
                  "diagnosedByActorId": 200,
                  "diagnosticResult": "Test",
                  "correctiveAction": "Test",
                  "repairResult": "SUCCESS"
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/{id}/repair-logs",
                                ticket.getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isNotFound());

        assertFalse(
                maintenanceRepairLogRepository
                        .existsByTenantIdAndTicketId(
                                2L,
                                ticket.getId()
                        )
        );
    }
}