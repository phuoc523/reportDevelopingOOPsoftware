package com.scentstation.operation.maintenance.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.maintenance.entity.PostMaintenanceTest;
import com.scentstation.operation.maintenance.enums.MaintenanceAuditAction;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
import com.scentstation.operation.maintenance.repository.MaintenanceAlertLinkRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceAssignmentHistoryRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceAuditEventRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceChecklistRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceRepairLogRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import com.scentstation.operation.maintenance.repository.PostMaintenanceTestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MaintenanceWorkflowEndToEndTest {

    private static final long TENANT_A = 1091L;
    private static final long TENANT_B = 1092L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private MaintenanceAlertLinkRepository alertLinkRepository;

    @Autowired
    private MaintenanceAuditEventRepository auditRepository;

    @BeforeEach
    void clearMaintenanceData() {
        auditRepository.deleteAll();
        alertLinkRepository.deleteAll();
        postTestRepository.deleteAll();
        repairRepository.deleteAll();
        checklistRepository.deleteAll();
        assignmentRepository.deleteAll();
        ticketRepository.deleteAll();
    }

    @Test
    void shouldCompleteFullWorkflowThroughHttpAndPreserveHistoryAfterReopen()
            throws Exception {
        JsonNode created = createTicket(TENANT_A, 501L);
        long ticketId = created.get("id").asLong();
        assertEquals("OPEN", created.get("status").asText());
        assertTrue(created.get("closedAt").isNull());

        JsonNode updated = putJson(
                "/api/maintenance/tickets/%d?tenantId=%d".formatted(ticketId, TENANT_A),
                """
                        {
                          "title":"Updated pump failure",
                          "description":"Pump seal requires replacement",
                          "category":"PUMP",
                          "severity":"MAJOR",
                          "priority":"HIGH",
                          "status":"COMPLETED",
                          "closedAt":"2026-01-01T00:00:00"
                        }
                        """,
                200
        );
        assertEquals("OPEN", updated.get("status").asText());
        assertTrue(updated.get("closedAt").isNull());

        JsonNode assigned = postJson(
                "/api/maintenance/tickets/%d/assignment".formatted(ticketId),
                assignment(TENANT_A, 701L, 601L),
                200
        );
        assertEquals("ASSIGNED", assigned.get("ticketStatus").asText());

        JsonNode acknowledged = postJson(
                "/api/maintenance/tickets/%d/assignment/acknowledge".formatted(ticketId),
                """
                        {"tenantId":%d,"technicianActorId":701}
                        """.formatted(TENANT_A),
                200
        );
        assertEquals("IN_PROGRESS", acknowledged.get("ticketStatus").asText());

        JsonNode required = postJson(
                "/api/maintenance/tickets/%d/checklist".formatted(ticketId),
                checklist(TENANT_A, "Inspect pump seal", true),
                201
        );
        postJson(
                "/api/maintenance/tickets/%d/checklist".formatted(ticketId),
                checklist(TENANT_A, "Clean enclosure", false),
                201
        );
        postJson(
                "/api/maintenance/tickets/%d/checklist/%d/complete"
                        .formatted(ticketId, required.get("id").asLong()),
                """
                        {"tenantId":%d,"completedByActorId":701,"resultNote":"Seal checked"}
                        """.formatted(TENANT_A),
                200
        );

        postJson(
                "/api/maintenance/tickets/%d/repair-logs".formatted(ticketId),
                repair(TENANT_A),
                201
        );
        JsonNode requested = postJson(
                "/api/maintenance/tickets/%d/post-tests".formatted(ticketId),
                """
                        {"tenantId":%d,"requestedByActorId":701}
                        """.formatted(TENANT_A),
                201
        );
        assertEquals("REQUESTED", requested.get("status").asText());

        postJson(
                "/api/maintenance/post-tests/results",
                result(TENANT_A, requested.get("externalTestId").asText(), "PASSED"),
                200
        );
        JsonNode closed = postJson(
                "/api/maintenance/tickets/%d/close".formatted(ticketId),
                """
                        {"tenantId":%d,"closedByActorId":801}
                        """.formatted(TENANT_A),
                200
        );
        assertEquals("COMPLETED", closed.get("status").asText());
        assertFalse(closed.get("closedAt").isNull());

        JsonNode historyBeforeReopen = getJson(
                "/api/maintenance/tickets/%d/history?tenantId=%d"
                        .formatted(ticketId, TENANT_A),
                200
        );
        assertHistory(historyBeforeReopen, "COMPLETED");

        List<MaintenanceAuditAction> actions = auditRepository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(TENANT_A, ticketId)
                .stream()
                .map(event -> event.getAction())
                .toList();
        assertTrue(actions.containsAll(List.of(
                MaintenanceAuditAction.TICKET_CREATED,
                MaintenanceAuditAction.TICKET_UPDATED,
                MaintenanceAuditAction.TECHNICIAN_ASSIGNED,
                MaintenanceAuditAction.ASSIGNMENT_ACKNOWLEDGED,
                MaintenanceAuditAction.CHECKLIST_CREATED,
                MaintenanceAuditAction.CHECKLIST_COMPLETED,
                MaintenanceAuditAction.REPAIR_RECORDED,
                MaintenanceAuditAction.POST_TEST_REQUESTED,
                MaintenanceAuditAction.POST_TEST_PASSED,
                MaintenanceAuditAction.TICKET_CLOSED
        )));

        JsonNode reopened = postJson(
                "/api/maintenance/tickets/%d/reopen".formatted(ticketId),
                """
                        {"tenantId":%d,"reopenedByActorId":901,"reason":"Issue recurred"}
                        """.formatted(TENANT_A),
                200
        );
        assertEquals("IN_PROGRESS", reopened.get("status").asText());
        assertTrue(reopened.get("closedAt").isNull());

        JsonNode historyAfterReopen = getJson(
                "/api/maintenance/tickets/%d/history?tenantId=%d"
                        .formatted(ticketId, TENANT_A),
                200
        );
        assertHistory(historyAfterReopen, "IN_PROGRESS");
        assertEquals(1, ticketRepository.findAllByTenantId(TENANT_A).size());
        assertEquals(2, assignmentRepository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(TENANT_A, ticketId)
                .size());
        assertEquals(2, checklistRepository.countByTenantIdAndTicketId(TENANT_A, ticketId));
        assertEquals(1, repairRepository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(TENANT_A, ticketId)
                .size());
        assertEquals(1, postTestRepository
                .findAllByTenantIdAndTicketIdOrderByRequestedAtAscIdAsc(TENANT_A, ticketId)
                .size());
        assertTrue(auditRepository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(TENANT_A, ticketId)
                .stream()
                .anyMatch(event -> event.getAction() == MaintenanceAuditAction.TICKET_REOPENED));
    }

    @Test
    void shouldRejectEveryInvalidCloseStageAndKeepFinalTestImmutable()
            throws Exception {
        long ticketId = createTicket(TENANT_A, 502L).get("id").asLong();
        JsonNode required = postJson(
                "/api/maintenance/tickets/%d/checklist".formatted(ticketId),
                checklist(TENANT_A, "Required safety check", true),
                201
        );

        close(ticketId, TENANT_A, 400);
        postJson(
                "/api/maintenance/tickets/%d/checklist/%d/complete"
                        .formatted(ticketId, required.get("id").asLong()),
                """
                        {"tenantId":%d,"completedByActorId":702}
                        """.formatted(TENANT_A),
                200
        );
        close(ticketId, TENANT_A, 400);

        postJson(
                "/api/maintenance/tickets/%d/repair-logs".formatted(ticketId),
                repair(TENANT_A),
                201
        );
        close(ticketId, TENANT_A, 400);

        JsonNode requested = postJson(
                "/api/maintenance/tickets/%d/post-tests".formatted(ticketId),
                """
                        {"tenantId":%d,"requestedByActorId":702}
                        """.formatted(TENANT_A),
                201
        );
        String externalTestId = requested.get("externalTestId").asText();
        postJson(
                "/api/maintenance/post-tests/results",
                result(TENANT_A, externalTestId, "FAILED"),
                200
        );
        close(ticketId, TENANT_A, 400);

        PostMaintenanceTest failed = postTestRepository
                .findByTenantIdAndExternalTestId(TENANT_A, externalTestId)
                .orElseThrow();
        LocalDateTime completedAt = failed.getCompletedAt();
        assertEquals(PostMaintenanceTestStatus.FAILED, failed.getStatus());
        assertNotNull(completedAt);

        postJson(
                "/api/maintenance/post-tests/results",
                result(TENANT_A, externalTestId, "PASSED"),
                400
        );
        PostMaintenanceTest unchanged = postTestRepository
                .findByTenantIdAndExternalTestId(TENANT_A, externalTestId)
                .orElseThrow();
        assertEquals(PostMaintenanceTestStatus.FAILED, unchanged.getStatus());
        assertEquals(completedAt, unchanged.getCompletedAt());
        assertEquals(1, auditRepository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(TENANT_A, ticketId)
                .stream()
                .filter(event -> event.getAction() == MaintenanceAuditAction.POST_TEST_FAILED)
                .count());
        assertEquals(MaintenanceStatus.OPEN, ticketRepository
                .findByIdAndTenantId(ticketId, TENANT_A)
                .orElseThrow()
                .getStatus());

        postJson(
                "/api/maintenance/tickets/%d/reopen".formatted(ticketId),
                """
                        {"tenantId":%d,"reopenedByActorId":902,"reason":"Not completed"}
                        """.formatted(TENANT_A),
                400
        );
    }

    @Test
    void shouldRejectTenantBAcrossTheMaintenanceWorkflow() throws Exception {
        long ticketId = createTicket(TENANT_A, 503L).get("id").asLong();
        JsonNode required = postJson(
                "/api/maintenance/tickets/%d/checklist".formatted(ticketId),
                checklist(TENANT_A, "Tenant isolation check", true),
                201
        );
        postJson(
                "/api/maintenance/tickets/%d/checklist/%d/complete"
                        .formatted(ticketId, required.get("id").asLong()),
                """
                        {"tenantId":%d,"completedByActorId":703}
                        """.formatted(TENANT_A),
                200
        );
        postJson(
                "/api/maintenance/tickets/%d/repair-logs".formatted(ticketId),
                repair(TENANT_A),
                201
        );
        JsonNode requested = postJson(
                "/api/maintenance/tickets/%d/post-tests".formatted(ticketId),
                """
                        {"tenantId":%d,"requestedByActorId":703}
                        """.formatted(TENANT_A),
                201
        );

        getJson("/api/maintenance/tickets/%d?tenantId=%d".formatted(ticketId, TENANT_B), 404);
        postJson(
                "/api/maintenance/tickets/%d/assignment".formatted(ticketId),
                assignment(TENANT_B, 704L, 604L),
                404
        );
        postJson(
                "/api/maintenance/tickets/%d/checklist".formatted(ticketId),
                checklist(TENANT_B, "Cross tenant", true),
                404
        );
        postJson(
                "/api/maintenance/tickets/%d/repair-logs".formatted(ticketId),
                repair(TENANT_B),
                404
        );
        postJson(
                "/api/maintenance/tickets/%d/post-tests".formatted(ticketId),
                """
                        {"tenantId":%d,"requestedByActorId":704}
                        """.formatted(TENANT_B),
                404
        );
        postJson(
                "/api/maintenance/post-tests/results",
                result(TENANT_B, requested.get("externalTestId").asText(), "PASSED"),
                404
        );
        close(ticketId, TENANT_B, 404);
        postJson(
                "/api/maintenance/tickets/%d/reopen".formatted(ticketId),
                """
                        {"tenantId":%d,"reopenedByActorId":904,"reason":"Cross tenant"}
                        """.formatted(TENANT_B),
                404
        );
        getJson(
                "/api/maintenance/tickets/%d/history?tenantId=%d"
                        .formatted(ticketId, TENANT_B),
                404
        );

        assertEquals(1, ticketRepository.findAllByTenantId(TENANT_A).size());
        assertTrue(ticketRepository.findAllByTenantId(TENANT_B).isEmpty());
        assertEquals(PostMaintenanceTestStatus.REQUESTED, postTestRepository
                .findByTenantIdAndExternalTestId(
                        TENANT_A,
                        requested.get("externalTestId").asText()
                )
                .orElseThrow()
                .getStatus());
    }

    private JsonNode createTicket(long tenantId, long actorId) throws Exception {
        return postJson(
                "/api/maintenance/tickets",
                """
                        {
                          "tenantId":%d,
                          "machineId":10901,
                          "locationId":10902,
                          "title":"Pump failure",
                          "description":"Pump cannot dispense fragrance",
                          "category":"PUMP",
                          "severity":"MAJOR",
                          "priority":"HIGH",
                          "reportedByActorId":%d,
                          "status":"COMPLETED",
                          "closedAt":"2026-01-01T00:00:00"
                        }
                        """.formatted(tenantId, actorId),
                201
        );
    }

    private String assignment(long tenantId, long technicianId, long actorId) {
        return """
                {"tenantId":%d,"technicianActorId":%d,"assignedByActorId":%d}
                """.formatted(tenantId, technicianId, actorId);
    }

    private String checklist(long tenantId, String itemName, boolean required) {
        return """
                {"tenantId":%d,"itemName":"%s","required":%s}
                """.formatted(tenantId, itemName, required);
    }

    private String repair(long tenantId) {
        return """
                {
                  "tenantId":%d,
                  "diagnosedByActorId":701,
                  "diagnosticResult":"Seal worn",
                  "correctiveAction":"Replace seal",
                  "repairResult":"SUCCESS"
                }
                """.formatted(tenantId);
    }

    private String result(long tenantId, String externalTestId, String result) {
        return """
                {
                  "tenantId":%d,
                  "externalTestId":"%s",
                  "status":"%s",
                  "resultMessage":"%s"
                }
                """.formatted(tenantId, externalTestId, result, result);
    }

    private void close(long ticketId, long tenantId, int expectedStatus) throws Exception {
        postJson(
                "/api/maintenance/tickets/%d/close".formatted(ticketId),
                """
                        {"tenantId":%d,"closedByActorId":803}
                        """.formatted(tenantId),
                expectedStatus
        );
    }

    private void assertHistory(JsonNode history, String status) {
        assertEquals(status, history.at("/ticket/status").asText());
        assertEquals(2, history.get("assignmentHistory").size());
        assertEquals(2, history.get("checklist").size());
        assertEquals(1, history.get("repairLogs").size());
        assertEquals(1, history.get("postMaintenanceTests").size());
        assertTrue(history.get("alertLink").isNull());
    }

    private JsonNode postJson(String path, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return response.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response);
    }

    private JsonNode putJson(String path, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(put(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode getJson(String path, int expectedStatus) throws Exception {
        String response = mockMvc.perform(get(path))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return response.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response);
    }
}
