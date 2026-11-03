package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.entity.*;
import com.scentstation.operation.maintenance.enums.*;
import com.scentstation.operation.maintenance.exception.MaintenanceNotFoundException;
import com.scentstation.operation.maintenance.mapper.*;
import com.scentstation.operation.maintenance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaintenanceHistoryServiceTest {

    private MaintenanceTicketRepository ticketRepository;
    private MaintenanceAssignmentHistoryRepository assignmentRepository;
    private MaintenanceChecklistRepository checklistRepository;
    private MaintenanceRepairLogRepository repairRepository;
    private PostMaintenanceTestRepository postTestRepository;
    private MaintenanceAlertLinkRepository alertRepository;
    private MaintenanceHistoryService service;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(MaintenanceTicketRepository.class);
        assignmentRepository = mock(MaintenanceAssignmentHistoryRepository.class);
        checklistRepository = mock(MaintenanceChecklistRepository.class);
        repairRepository = mock(MaintenanceRepairLogRepository.class);
        postTestRepository = mock(PostMaintenanceTestRepository.class);
        alertRepository = mock(MaintenanceAlertLinkRepository.class);
        service = new MaintenanceHistoryService(
                ticketRepository,
                assignmentRepository,
                checklistRepository,
                repairRepository,
                postTestRepository,
                alertRepository,
                new MaintenanceTicketMapper(),
                new MaintenanceChecklistMapper(),
                new MaintenanceRepairLogMapper(),
                new PostMaintenanceTestMapper()
        );
    }

    @Test
    void shouldAggregateEveryTicketHistorySection() {
        MaintenanceTicket ticket = ticket(1L, 10L, 100L);
        when(ticketRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(ticket));
        when(assignmentRepository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(1L, 10L))
                .thenReturn(List.of(MaintenanceAssignmentHistory.builder()
                        .id(1L).tenantId(1L).ticketId(10L)
                        .technicianActorId(7L).performedByActorId(8L)
                        .eventType(MaintenanceAssignmentEventType.ASSIGNED).build()));
        when(checklistRepository.findAllByTenantIdAndTicketIdOrderByIdAsc(1L, 10L))
                .thenReturn(List.of(MaintenanceChecklistItem.builder()
                        .id(2L).tenantId(1L).ticketId(10L)
                        .itemName("Check pump").required(true).completed(true).build()));
        when(repairRepository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(1L, 10L))
                .thenReturn(List.of(MaintenanceRepairLog.builder()
                        .id(3L).tenantId(1L).ticketId(10L).diagnosedByActorId(7L)
                        .diagnosticResult("Seal damaged").correctiveAction("Replace seal")
                        .repairResult(MaintenanceRepairResult.SUCCESS).build()));
        when(postTestRepository
                .findAllByTenantIdAndTicketIdOrderByRequestedAtAscIdAsc(1L, 10L))
                .thenReturn(List.of(PostMaintenanceTest.builder()
                        .id(4L).tenantId(1L).ticketId(10L).machineId(100L)
                        .requestedByActorId(7L).status(PostMaintenanceTestStatus.PASSED)
                        .externalTestId("EXT-1").build()));
        when(alertRepository.findFirstByTenantIdAndTicketIdOrderByIdAsc(1L, 10L))
                .thenReturn(Optional.of(MaintenanceAlertLink.builder()
                        .tenantId(1L).ticketId(10L).machineId(100L)
                        .sourceAlertId("ALERT-1").alertType("PUMP_FAILURE")
                        .occurredAt(java.time.LocalDateTime.now()).build()));

        var response = service.getTicketHistory(1L, 10L);

        assertEquals(10L, response.getTicket().getId());
        assertEquals(MaintenanceAssignmentEventType.ASSIGNED,
                response.getAssignmentHistory().get(0).getEventType());
        assertEquals("Check pump", response.getChecklist().get(0).getItemName());
        assertEquals("Seal damaged", response.getRepairLogs().get(0).getDiagnosticResult());
        assertEquals(PostMaintenanceTestStatus.PASSED,
                response.getPostMaintenanceTests().get(0).getStatus());
        assertEquals("ALERT-1", response.getAlertLink().getSourceAlertId());
        assertEquals(MaintenanceStatus.OPEN, ticket.getStatus());
    }

    @Test
    void shouldReturnEmptyCollectionsAndNullAlert() {
        when(ticketRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(ticket(1L, 10L, 100L)));

        var response = service.getTicketHistory(1L, 10L);

        assertTrue(response.getAssignmentHistory().isEmpty());
        assertTrue(response.getChecklist().isEmpty());
        assertTrue(response.getRepairLogs().isEmpty());
        assertTrue(response.getPostMaintenanceTests().isEmpty());
        assertNull(response.getAlertLink());
    }

    @Test
    void shouldRejectCrossTenantTicketHistory() {
        when(ticketRepository.findByIdAndTenantId(10L, 2L))
                .thenReturn(Optional.empty());

        assertThrows(
                MaintenanceNotFoundException.class,
                () -> service.getTicketHistory(2L, 10L)
        );
        verifyNoInteractions(
                assignmentRepository,
                checklistRepository,
                repairRepository,
                postTestRepository,
                alertRepository
        );
    }

    @Test
    void shouldReturnOnlyOrderedMachineTicketsWithoutNestedQueries() {
        MaintenanceTicket newest = ticket(1L, 12L, 100L);
        MaintenanceTicket oldest = ticket(1L, 11L, 100L);
        when(ticketRepository
                .findAllByTenantIdAndMachineIdOrderByCreatedAtDescIdDesc(1L, 100L))
                .thenReturn(List.of(newest, oldest));

        var response = service.getMachineHistory(1L, 100L);

        assertEquals(List.of(12L, 11L), response.getTickets().stream()
                .map(ticketResponse -> ticketResponse.getId()).toList());
        verifyNoInteractions(
                assignmentRepository,
                checklistRepository,
                repairRepository,
                postTestRepository,
                alertRepository
        );
    }

    private MaintenanceTicket ticket(Long tenantId, Long id, Long machineId) {
        return MaintenanceTicket.builder()
                .id(id).tenantId(tenantId).machineId(machineId)
                .title("Ticket " + id).description("Maintenance").category("TEST")
                .severity(MaintenanceSeverity.MODERATE)
                .priority(MaintenancePriority.MEDIUM)
                .status(MaintenanceStatus.OPEN)
                .reportedByActorId(9L)
                .build();
    }
}
