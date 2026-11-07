package com.scentstation.operation.maintenance.config;

import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenanceAssignmentEventType;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceRepairResult;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
import com.scentstation.operation.maintenance.repository.MaintenanceAlertLinkRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceAssignmentHistoryRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceAuditEventRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceChecklistRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceRepairLogRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import com.scentstation.operation.maintenance.repository.PostMaintenanceTestRepository;
import com.scentstation.operation.maintenance.service.MaintenanceHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MaintenanceSampleDataInitializerTest {

    @Autowired
    private MaintenanceSampleDataInitializer initializer;

    @Autowired
    private MaintenanceTicketRepository ticketRepository;

    @Autowired
    private MaintenanceAssignmentHistoryRepository assignmentRepository;

    @Autowired
    private MaintenanceChecklistRepository checklistRepository;

    @Autowired
    private MaintenanceRepairLogRepository repairRepository;

    @Autowired
    private PostMaintenanceTestRepository postTestRepository;

    @Autowired
    private MaintenanceAlertLinkRepository alertRepository;

    @Autowired
    private MaintenanceAuditEventRepository auditRepository;

    @Autowired
    private MaintenanceHistoryService historyService;

    @Test
    void shouldCreateConsistentDemoScenariosForTwoTenants() {
        List<MaintenanceTicket> tenantA = ticketRepository.findAllByTenantId(
                MaintenanceSampleDataInitializer.TENANT_A
        );
        List<MaintenanceTicket> tenantB = ticketRepository.findAllByTenantId(
                MaintenanceSampleDataInitializer.TENANT_B
        );

        assertEquals(9, tenantA.size());
        assertEquals(1, tenantB.size());
        assertEquals(Set.of(101L, 102L), tenantA.stream()
                .map(MaintenanceTicket::getMachineId)
                .collect(Collectors.toSet()));
        assertEquals(Set.of(201L), tenantB.stream()
                .map(MaintenanceTicket::getMachineId)
                .collect(Collectors.toSet()));
        assertTrue(tenantA.stream().noneMatch(ticket ->
                ticket.getTenantId().equals(MaintenanceSampleDataInitializer.TENANT_B)));

        Set<MaintenanceStatus> statuses = tenantA.stream()
                .map(MaintenanceTicket::getStatus)
                .collect(Collectors.toSet());
        assertTrue(statuses.containsAll(Set.of(
                MaintenanceStatus.OPEN,
                MaintenanceStatus.ASSIGNED,
                MaintenanceStatus.IN_PROGRESS,
                MaintenanceStatus.WAITING_FOR_PARTS,
                MaintenanceStatus.READY_FOR_TEST,
                MaintenanceStatus.COMPLETED
        )));

        MaintenanceTicket completed = ticket(tenantA, "Demo - Maintenance completed");
        assertNotNull(completed.getClosedAt());
        assertFalse(completed.getClosedAt().isBefore(completed.getCreatedAt()));
        assertFalse(checklistRepository
                .existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(
                        completed.getTenantId(), completed.getId()
                ));
        assertTrue(repairRepository.existsByTenantIdAndTicketId(
                completed.getTenantId(), completed.getId()
        ));
        var completedTest = postTestRepository
                .findFirstByTenantIdAndTicketIdOrderByRequestedAtDescIdDesc(
                        completed.getTenantId(), completed.getId()
                )
                .orElseThrow();
        assertEquals(PostMaintenanceTestStatus.PASSED, completedTest.getStatus());
        assertFalse(completedTest.getCompletedAt().isBefore(completedTest.getRequestedAt()));

        MaintenanceTicket failed = ticket(tenantA, "Demo - Post test failed");
        assertFalse(failed.getStatus() == MaintenanceStatus.COMPLETED);
        assertEquals(PostMaintenanceTestStatus.FAILED, postTestRepository
                .findFirstByTenantIdAndTicketIdOrderByRequestedAtDescIdDesc(
                        failed.getTenantId(), failed.getId()
                )
                .orElseThrow()
                .getStatus());

        MaintenanceTicket reopened = ticket(tenantA, "Demo - Reopened ticket");
        assertEquals(MaintenanceStatus.IN_PROGRESS, reopened.getStatus());
        assertNull(reopened.getClosedAt());
        assertTrue(repairRepository.existsByTenantIdAndTicketId(
                reopened.getTenantId(), reopened.getId()
        ));

        MaintenanceTicket critical = ticket(
                tenantA,
                "Demo - Critical temperature alert"
        );
        assertTrue(critical.isCritical());
        assertEquals(MaintenanceSeverity.CRITICAL, critical.getSeverity());
        assertEquals(MaintenancePriority.URGENT, critical.getPriority());
        assertEquals("DEMO-ALERT-TEMP-001", alertRepository
                .findFirstByTenantIdAndTicketIdOrderByIdAsc(
                        critical.getTenantId(), critical.getId()
                )
                .orElseThrow()
                .getSourceAlertId());

        Set<MaintenanceAssignmentEventType> assignmentTypes = assignmentRepository
                .findAll()
                .stream()
                .map(history -> history.getEventType())
                .collect(Collectors.toSet());
        assertTrue(assignmentTypes.containsAll(Set.of(
                MaintenanceAssignmentEventType.ASSIGNED,
                MaintenanceAssignmentEventType.REASSIGNED,
                MaintenanceAssignmentEventType.ACKNOWLEDGED
        )));
        MaintenanceTicket inProgress = ticket(tenantA, "Demo - Vệ sinh group head");
        var orderedAssignments = assignmentRepository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(
                        inProgress.getTenantId(), inProgress.getId()
                );
        assertEquals(List.of(
                MaintenanceAssignmentEventType.ASSIGNED,
                MaintenanceAssignmentEventType.REASSIGNED,
                MaintenanceAssignmentEventType.ACKNOWLEDGED
        ), orderedAssignments.stream().map(history -> history.getEventType()).toList());
        assertTrue(checklistRepository.findAll().stream().anyMatch(item -> item.isRequired()));
        assertTrue(checklistRepository.findAll().stream().anyMatch(item -> !item.isRequired()));
        assertEquals(Set.of(
                MaintenanceRepairResult.SUCCESS,
                MaintenanceRepairResult.PARTIAL,
                MaintenanceRepairResult.FAILED
        ), repairRepository.findAll().stream()
                .map(log -> log.getRepairResult())
                .collect(Collectors.toSet()));
        assertTrue(postTestRepository.findAll().stream()
                .map(test -> test.getStatus())
                .collect(Collectors.toSet())
                .containsAll(Set.of(
                        PostMaintenanceTestStatus.REQUESTED,
                        PostMaintenanceTestStatus.FAILED,
                        PostMaintenanceTestStatus.PASSED
                )));
        assertFalse(auditRepository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(
                        completed.getTenantId(), completed.getId()
                )
                .isEmpty());

        var history = historyService.getTicketHistory(
                completed.getTenantId(), completed.getId()
        );
        assertFalse(history.getAssignmentHistory().isEmpty());
        assertFalse(history.getChecklist().isEmpty());
        assertFalse(history.getRepairLogs().isEmpty());
        assertFalse(history.getPostMaintenanceTests().isEmpty());
    }

    @Test
    void shouldNotCreateDuplicatesWhenInitializerRunsAgain() throws Exception {
        List<Long> before = counts();

        initializer.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(before, counts());
    }

    private List<Long> counts() {
        return List.of(
                ticketRepository.count(),
                assignmentRepository.count(),
                checklistRepository.count(),
                repairRepository.count(),
                postTestRepository.count(),
                alertRepository.count(),
                auditRepository.count()
        );
    }

    private MaintenanceTicket ticket(List<MaintenanceTicket> tickets, String title) {
        return tickets.stream()
                .filter(ticket -> ticket.getTitle().equals(title))
                .findFirst()
                .orElseThrow();
    }
}
