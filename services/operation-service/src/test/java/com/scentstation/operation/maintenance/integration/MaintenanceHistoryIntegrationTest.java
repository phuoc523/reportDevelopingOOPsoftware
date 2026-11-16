package com.scentstation.operation.maintenance.integration;

import com.scentstation.operation.maintenance.entity.*;
import com.scentstation.operation.maintenance.enums.*;
import com.scentstation.operation.maintenance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MaintenanceHistoryIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MaintenanceTicketRepository ticketRepository;
    @Autowired private MaintenanceAssignmentHistoryRepository assignmentRepository;
    @Autowired private MaintenanceChecklistRepository checklistRepository;
    @Autowired private MaintenanceRepairLogRepository repairRepository;
    @Autowired private PostMaintenanceTestRepository postTestRepository;
    @Autowired private MaintenanceAlertLinkRepository alertRepository;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        postTestRepository.deleteAll();
        repairRepository.deleteAll();
        checklistRepository.deleteAll();
        assignmentRepository.deleteAll();
        ticketRepository.deleteAll();
    }

    @Test
    void shouldReturnAllPersistedTicketHistoryWithoutMutation() throws Exception {
        MaintenanceTicket ticket = ticketRepository.saveAndFlush(ticket(1L, 100L, "Primary"));
        assignmentRepository.saveAndFlush(MaintenanceAssignmentHistory.builder()
                .tenantId(1L).ticketId(ticket.getId())
                .technicianActorId(7L).performedByActorId(8L)
                .eventType(MaintenanceAssignmentEventType.ASSIGNED).build());
        checklistRepository.saveAndFlush(MaintenanceChecklistItem.builder()
                .tenantId(1L).ticketId(ticket.getId()).itemName("Check pump")
                .required(true).completed(true).build());
        repairRepository.saveAndFlush(MaintenanceRepairLog.builder()
                .tenantId(1L).ticketId(ticket.getId()).diagnosedByActorId(7L)
                .diagnosticResult("Seal damaged").correctiveAction("Replace seal")
                .repairResult(MaintenanceRepairResult.SUCCESS).build());
        postTestRepository.saveAndFlush(PostMaintenanceTest.builder()
                .tenantId(1L).ticketId(ticket.getId()).machineId(100L)
                .requestedByActorId(7L).status(PostMaintenanceTestStatus.PASSED)
                .externalTestId("EXT-HISTORY").build());
        alertRepository.saveAndFlush(MaintenanceAlertLink.builder()
                .tenantId(1L).ticketId(ticket.getId()).machineId(100L)
                .sourceAlertId("ALERT-HISTORY").alertType("PUMP_FAILURE")
                .occurredAt(LocalDateTime.now()).build());

        long totalRows = assignmentRepository.count() + checklistRepository.count()
                + repairRepository.count() + postTestRepository.count()
                + alertRepository.count();

        mockMvc.perform(get("/api/maintenance/tickets/{id}/history", ticket.getId())
                        .param("tenantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.id").value(ticket.getId()))
                .andExpect(jsonPath("$.assignmentHistory[0].eventType").value("ASSIGNED"))
                .andExpect(jsonPath("$.checklist[0].itemName").value("Check pump"))
                .andExpect(jsonPath("$.repairLogs[0].diagnosticResult").value("Seal damaged"))
                .andExpect(jsonPath("$.postMaintenanceTests[0].status").value("PASSED"))
                .andExpect(jsonPath("$.alertLink.sourceAlertId").value("ALERT-HISTORY"));

        assertEquals(totalRows, assignmentRepository.count() + checklistRepository.count()
                + repairRepository.count() + postTestRepository.count()
                + alertRepository.count());
    }

    @Test
    void shouldIsolateAndOrderMachineHistory() throws Exception {
        MaintenanceTicket oldest = ticketRepository.saveAndFlush(ticket(1L, 100L, "Oldest"));
        MaintenanceTicket newest = ticketRepository.saveAndFlush(ticket(1L, 100L, "Newest"));
        ticketRepository.saveAndFlush(ticket(1L, 200L, "Other machine"));
        ticketRepository.saveAndFlush(ticket(2L, 100L, "Other tenant"));

        mockMvc.perform(get("/api/maintenance/machines/100/history")
                        .param("tenantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets.length()").value(2))
                .andExpect(jsonPath("$.tickets[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.tickets[1].id").value(oldest.getId()));
    }

    private MaintenanceTicket ticket(Long tenantId, Long machineId, String title) {
        return MaintenanceTicket.builder()
                .tenantId(tenantId).machineId(machineId)
                .title(title).description("Maintenance").category("TEST")
                .severity(MaintenanceSeverity.MODERATE)
                .priority(MaintenancePriority.MEDIUM)
                .status(MaintenanceStatus.OPEN)
                .reportedByActorId(9L)
                .build();
    }
}
