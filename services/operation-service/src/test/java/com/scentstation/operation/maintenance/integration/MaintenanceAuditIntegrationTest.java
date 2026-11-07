package com.scentstation.operation.maintenance.integration;

import com.scentstation.operation.maintenance.dto.request.AssignMaintenanceRequest;
import com.scentstation.operation.maintenance.dto.request.CloseMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.CompleteChecklistItemRequest;
import com.scentstation.operation.maintenance.dto.request.CreateChecklistItemRequest;
import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceRepairLogRequest;
import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.CriticalMaintenanceAlertRequest;
import com.scentstation.operation.maintenance.dto.request.PostMaintenanceTestResultRequest;
import com.scentstation.operation.maintenance.dto.request.ReopenMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.RequestPostMaintenanceTestRequest;
import com.scentstation.operation.maintenance.dto.request.UpdateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.entity.MaintenanceAuditEvent;
import com.scentstation.operation.maintenance.enums.MaintenanceAuditAction;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceRepairResult;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
import com.scentstation.operation.maintenance.exception.CriticalAlertAlreadyProcessedException;
import com.scentstation.operation.maintenance.exception.InvalidMaintenanceTicketException;
import com.scentstation.operation.maintenance.repository.MaintenanceAuditEventRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import com.scentstation.operation.maintenance.service.CriticalMaintenanceAlertService;
import com.scentstation.operation.maintenance.service.MaintenanceAssignmentService;
import com.scentstation.operation.maintenance.service.MaintenanceChecklistService;
import com.scentstation.operation.maintenance.service.MaintenanceRepairLogService;
import com.scentstation.operation.maintenance.service.MaintenanceTicketService;
import com.scentstation.operation.maintenance.service.PostMaintenanceTestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(MaintenanceAuditIntegrationTest.RollbackConfig.class)
class MaintenanceAuditIntegrationTest {

    @Autowired
    private MaintenanceTicketService ticketService;

    @Autowired
    private MaintenanceAssignmentService assignmentService;

    @Autowired
    private MaintenanceChecklistService checklistService;

    @Autowired
    private MaintenanceRepairLogService repairLogService;

    @Autowired
    private PostMaintenanceTestService postTestService;

    @Autowired
    private CriticalMaintenanceAlertService criticalAlertService;

    @Autowired
    private MaintenanceAuditEventRepository auditRepository;

    @Autowired
    private MaintenanceTicketRepository ticketRepository;

    @Autowired
    private RollbackProbe rollbackProbe;

    @Test
    void shouldRecordCompleteMaintenanceWorkflowInOrder() {
        long ticketId = ticketService.createTicket(ticketRequest(10L, 100L)).getId();

        UpdateMaintenanceTicketRequest update = new UpdateMaintenanceTicketRequest();
        update.setTitle("Updated pump failure");
        update.setDescription("Updated description");
        update.setCategory("PUMP");
        update.setSeverity(MaintenanceSeverity.MAJOR);
        update.setPriority(MaintenancePriority.HIGH);
        ticketService.updateTicket(10L, ticketId, update);

        assignmentService.assignTechnician(ticketId, assignment(10L, 200L, 101L));
        assignmentService.assignTechnician(ticketId, assignment(10L, 300L, 102L));
        assignmentService.acknowledgeAssignment(10L, ticketId, 300L);

        CreateChecklistItemRequest createItem = new CreateChecklistItemRequest();
        createItem.setTenantId(10L);
        createItem.setItemName("Check pump");
        createItem.setRequired(true);
        long itemId = checklistService.createChecklistItem(ticketId, createItem).getId();

        CompleteChecklistItemRequest completeItem = new CompleteChecklistItemRequest();
        completeItem.setTenantId(10L);
        completeItem.setCompletedByActorId(300L);
        completeItem.setResultNote("OK");
        checklistService.completeChecklistItem(ticketId, itemId, completeItem);

        repairLogService.createRepairLog(ticketId, repairRequest(10L, 300L));

        RequestPostMaintenanceTestRequest requestTest = new RequestPostMaintenanceTestRequest();
        requestTest.setTenantId(10L);
        requestTest.setRequestedByActorId(400L);
        String externalTestId = postTestService.requestTest(ticketId, requestTest)
                .getExternalTestId();
        postTestService.recordResult(result(10L, externalTestId, PostMaintenanceTestStatus.PASSED));

        CloseMaintenanceTicketRequest close = new CloseMaintenanceTicketRequest();
        close.setTenantId(10L);
        close.setClosedByActorId(500L);
        ticketService.closeTicket(ticketId, close);

        ReopenMaintenanceTicketRequest reopen = new ReopenMaintenanceTicketRequest();
        reopen.setTenantId(10L);
        reopen.setReopenedByActorId(600L);
        reopen.setReason("Issue recurred");
        ticketService.reopenTicket(ticketId, reopen);

        List<MaintenanceAuditEvent> events = auditRepository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(10L, ticketId);

        assertEquals(List.of(
                MaintenanceAuditAction.TICKET_CREATED,
                MaintenanceAuditAction.TICKET_UPDATED,
                MaintenanceAuditAction.TECHNICIAN_ASSIGNED,
                MaintenanceAuditAction.TECHNICIAN_REASSIGNED,
                MaintenanceAuditAction.ASSIGNMENT_ACKNOWLEDGED,
                MaintenanceAuditAction.CHECKLIST_CREATED,
                MaintenanceAuditAction.CHECKLIST_COMPLETED,
                MaintenanceAuditAction.REPAIR_RECORDED,
                MaintenanceAuditAction.POST_TEST_REQUESTED,
                MaintenanceAuditAction.POST_TEST_PASSED,
                MaintenanceAuditAction.TICKET_CLOSED,
                MaintenanceAuditAction.TICKET_REOPENED
        ), events.stream().map(MaintenanceAuditEvent::getAction).toList());
        assertTrue(events.stream().allMatch(event -> event.getTenantId().equals(10L)));
        assertTrue(events.stream().allMatch(event -> event.getTicketId().equals(ticketId)));
        assertTrue(events.stream().allMatch(event -> event.getCreatedAt() != null));
        assertFalse(events.stream().map(MaintenanceAuditEvent::getCreatedAt).toList()
                .stream().reduce((left, right) -> {
                    assertFalse(right.isBefore(left));
                    return right;
                }).isEmpty());
        assertEquals(100L, actor(events, MaintenanceAuditAction.TICKET_CREATED));
        assertNull(actor(events, MaintenanceAuditAction.TICKET_UPDATED));
        assertEquals(101L, actor(events, MaintenanceAuditAction.TECHNICIAN_ASSIGNED));
        assertEquals(102L, actor(events, MaintenanceAuditAction.TECHNICIAN_REASSIGNED));
        assertEquals(300L, actor(events, MaintenanceAuditAction.ASSIGNMENT_ACKNOWLEDGED));
        assertEquals(300L, actor(events, MaintenanceAuditAction.CHECKLIST_COMPLETED));
        assertEquals(300L, actor(events, MaintenanceAuditAction.REPAIR_RECORDED));
        assertEquals(400L, actor(events, MaintenanceAuditAction.POST_TEST_REQUESTED));
        assertEquals(500L, actor(events, MaintenanceAuditAction.TICKET_CLOSED));
        assertEquals(600L, actor(events, MaintenanceAuditAction.TICKET_REOPENED));
        assertTrue(event(events, MaintenanceAuditAction.TICKET_CLOSED)
                .getBeforeState().startsWith("status="));
        assertEquals("status=COMPLETED", event(events, MaintenanceAuditAction.TICKET_CLOSED)
                .getAfterState());
    }

    @Test
    void shouldRecordFailedResultAndIgnoreDuplicateFinalResult() {
        long ticketId = ticketService.createTicket(ticketRequest(20L, 110L)).getId();
        repairLogService.createRepairLog(ticketId, repairRequest(20L, 210L));

        RequestPostMaintenanceTestRequest request = new RequestPostMaintenanceTestRequest();
        request.setTenantId(20L);
        request.setRequestedByActorId(310L);
        String externalTestId = postTestService.requestTest(ticketId, request)
                .getExternalTestId();
        postTestService.recordResult(result(
                20L,
                externalTestId,
                PostMaintenanceTestStatus.FAILED
        ));

        long count = auditRepository.count();
        assertThrows(
                InvalidMaintenanceTicketException.class,
                () -> postTestService.recordResult(result(
                        20L,
                        externalTestId,
                        PostMaintenanceTestStatus.PASSED
                ))
        );

        assertEquals(count, auditRepository.count());
        assertEquals(1, auditRepository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(20L, ticketId)
                .stream()
                .filter(event -> event.getAction() == MaintenanceAuditAction.POST_TEST_FAILED)
                .count());
    }

    @Test
    void shouldRecordCriticalAlertOnlyOnce() {
        CriticalMaintenanceAlertRequest request = new CriticalMaintenanceAlertRequest();
        request.setTenantId(30L);
        request.setMachineId(99L);
        request.setAlertId("ALERT-107");
        request.setAlertType("PUMP_FAILURE");
        request.setMessage("Critical pressure");
        request.setOccurredAt(LocalDateTime.now());

        long ticketId = criticalAlertService.processCriticalAlert(request).getTicketId();
        long count = auditRepository.count();
        assertThrows(
                CriticalAlertAlreadyProcessedException.class,
                () -> criticalAlertService.processCriticalAlert(request)
        );

        assertEquals(count, auditRepository.count());
        MaintenanceAuditEvent event = event(
                auditRepository.findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(
                        30L,
                        ticketId
                ),
                MaintenanceAuditAction.CRITICAL_ALERT_TICKET_CREATED
        );
        assertEquals(0L, event.getActorId());
        assertEquals("CRITICAL_ALERT", event.getSource());
    }

    @Test
    void shouldRollbackBusinessAndAuditTogether() {
        long ticketsBefore = ticketRepository.count();
        long auditBefore = auditRepository.count();

        assertThrows(
                IllegalStateException.class,
                () -> rollbackProbe.createTicketThenFail(ticketRequest(40L, 140L))
        );

        assertEquals(ticketsBefore, ticketRepository.count());
        assertEquals(auditBefore, auditRepository.count());
    }

    private CreateMaintenanceTicketRequest ticketRequest(Long tenantId, Long actorId) {
        CreateMaintenanceTicketRequest request = new CreateMaintenanceTicketRequest();
        request.setTenantId(tenantId);
        request.setMachineId(900L + tenantId);
        request.setTitle("Pump failure");
        request.setDescription("Pump requires maintenance");
        request.setCategory("PUMP");
        request.setSeverity(MaintenanceSeverity.MAJOR);
        request.setPriority(MaintenancePriority.HIGH);
        request.setReportedByActorId(actorId);
        return request;
    }

    private AssignMaintenanceRequest assignment(Long tenantId, Long technician, Long actor) {
        AssignMaintenanceRequest request = new AssignMaintenanceRequest();
        request.setTenantId(tenantId);
        request.setTechnicianActorId(technician);
        request.setAssignedByActorId(actor);
        return request;
    }

    private CreateMaintenanceRepairLogRequest repairRequest(Long tenantId, Long actorId) {
        CreateMaintenanceRepairLogRequest request = new CreateMaintenanceRepairLogRequest();
        request.setTenantId(tenantId);
        request.setDiagnosedByActorId(actorId);
        request.setDiagnosticResult("Seal worn");
        request.setCorrectiveAction("Replace seal");
        request.setRepairResult(MaintenanceRepairResult.SUCCESS);
        return request;
    }

    private PostMaintenanceTestResultRequest result(
            Long tenantId,
            String externalTestId,
            PostMaintenanceTestStatus status
    ) {
        PostMaintenanceTestResultRequest request = new PostMaintenanceTestResultRequest();
        request.setTenantId(tenantId);
        request.setExternalTestId(externalTestId);
        request.setStatus(status);
        request.setResultMessage(status.name());
        return request;
    }

    private MaintenanceAuditEvent event(
            List<MaintenanceAuditEvent> events,
            MaintenanceAuditAction action
    ) {
        return events.stream()
                .filter(event -> event.getAction() == action)
                .findFirst()
                .orElseThrow();
    }

    private Long actor(
            List<MaintenanceAuditEvent> events,
            MaintenanceAuditAction action
    ) {
        return event(events, action).getActorId();
    }

    @TestConfiguration
    static class RollbackConfig {

        @Bean
        RollbackProbe rollbackProbe(MaintenanceTicketService ticketService) {
            return new RollbackProbe(ticketService);
        }
    }

    static class RollbackProbe {

        private final MaintenanceTicketService ticketService;

        RollbackProbe(MaintenanceTicketService ticketService) {
            this.ticketService = ticketService;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void createTicketThenFail(CreateMaintenanceTicketRequest request) {
            ticketService.createTicket(request);
            throw new IllegalStateException("business failure");
        }
    }
}
