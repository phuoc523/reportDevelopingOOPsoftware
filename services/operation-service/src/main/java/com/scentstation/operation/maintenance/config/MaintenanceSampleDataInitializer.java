package com.scentstation.operation.maintenance.config;

import com.scentstation.operation.maintenance.entity.MaintenanceAlertLink;
import com.scentstation.operation.maintenance.entity.MaintenanceAssignmentHistory;
import com.scentstation.operation.maintenance.entity.MaintenanceAuditEvent;
import com.scentstation.operation.maintenance.entity.MaintenanceChecklistItem;
import com.scentstation.operation.maintenance.entity.MaintenanceRepairLog;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.entity.PostMaintenanceTest;
import com.scentstation.operation.maintenance.enums.MaintenanceAssignmentEventType;
import com.scentstation.operation.maintenance.enums.MaintenanceAuditAction;
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
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@Profile("local")
@RequiredArgsConstructor
public class MaintenanceSampleDataInitializer implements ApplicationRunner {

    public static final Long TENANT_A = 1001L;
    public static final Long TENANT_B = 2001L;
    public static final String TENANT_A_MARKER = "Demo - Máy rò nước";
    public static final String TENANT_B_MARKER = "Demo B - Lỗi cảm biến";

    private static final Long SYSTEM_ACTOR_ID = 0L;
    private static final Long MANAGER_ID = 5001L;
    private static final Long TECHNICIAN_A_ID = 5101L;
    private static final Long TECHNICIAN_B_ID = 5102L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 10, 0);

    private final MaintenanceTicketRepository ticketRepository;
    private final MaintenanceAssignmentHistoryRepository assignmentRepository;
    private final MaintenanceChecklistRepository checklistRepository;
    private final MaintenanceRepairLogRepository repairRepository;
    private final PostMaintenanceTestRepository postTestRepository;
    private final MaintenanceAlertLinkRepository alertLinkRepository;
    private final MaintenanceAuditEventRepository auditRepository;

    // Tạo sample data theo từng tenant và bỏ qua tenant đã có marker.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!ticketRepository.existsByTenantIdAndTitle(TENANT_A, TENANT_A_MARKER)) {
            seedTenantA();
        }
        if (!ticketRepository.existsByTenantIdAndTitle(TENANT_B, TENANT_B_MARKER)) {
            seedTenantB();
        }
    }

    // Tạo chín scenario đầy đủ cho tenant demo chính.
    private void seedTenantA() {
        MaintenanceTicket open = ticket(
                TENANT_A, 101L, TENANT_A_MARKER, MaintenanceStatus.OPEN,
                MaintenancePriority.HIGH, MaintenanceSeverity.MODERATE,
                null, false, null
        );
        audit(open, MaintenanceAuditAction.TICKET_CREATED, MANAGER_ID, null,
                "status=OPEN", "MAINTENANCE");

        MaintenanceTicket assigned = ticket(
                TENANT_A, 101L, "Demo - Máy mất áp suất", MaintenanceStatus.ASSIGNED,
                MaintenancePriority.HIGH, MaintenanceSeverity.MAJOR,
                TECHNICIAN_A_ID, false, null
        );
        assignment(assigned, null, TECHNICIAN_A_ID, MANAGER_ID,
                MaintenanceAssignmentEventType.ASSIGNED);
        audit(assigned, MaintenanceAuditAction.TECHNICIAN_ASSIGNED, MANAGER_ID,
                "technician=null", "technician=" + TECHNICIAN_A_ID, "MAINTENANCE");

        MaintenanceTicket inProgress = ticket(
                TENANT_A, 102L, "Demo - Vệ sinh group head", MaintenanceStatus.IN_PROGRESS,
                MaintenancePriority.MEDIUM, MaintenanceSeverity.MODERATE,
                TECHNICIAN_B_ID, false, null
        );
        assignment(inProgress, null, TECHNICIAN_A_ID, MANAGER_ID,
                MaintenanceAssignmentEventType.ASSIGNED);
        assignment(inProgress, TECHNICIAN_A_ID, TECHNICIAN_B_ID, MANAGER_ID,
                MaintenanceAssignmentEventType.REASSIGNED);
        assignment(inProgress, TECHNICIAN_B_ID, TECHNICIAN_B_ID, TECHNICIAN_B_ID,
                MaintenanceAssignmentEventType.ACKNOWLEDGED);
        checklist(inProgress, "Kiểm tra áp suất", true, false, null);
        checklist(inProgress, "Vệ sinh bề mặt", false, true, TECHNICIAN_B_ID);
        audit(inProgress, MaintenanceAuditAction.ASSIGNMENT_ACKNOWLEDGED,
                TECHNICIAN_B_ID, "status=ASSIGNED", "status=IN_PROGRESS", "MAINTENANCE");

        MaintenanceTicket waitingParts = ticket(
                TENANT_A, 102L, "Demo - Thay van bơm", MaintenanceStatus.WAITING_FOR_PARTS,
                MaintenancePriority.HIGH, MaintenanceSeverity.MAJOR,
                TECHNICIAN_A_ID, false, null
        );
        checklist(waitingParts, "Nhận van thay thế", true, false, null);
        repair(waitingParts, TECHNICIAN_A_ID, MaintenanceRepairResult.PARTIAL,
                "Đã chẩn đoán, đang chờ van", "Đặt linh kiện thay thế");

        MaintenanceTicket requestedTest = ticket(
                TENANT_A, 101L, "Demo - Đang chờ post test", MaintenanceStatus.READY_FOR_TEST,
                MaintenancePriority.MEDIUM, MaintenanceSeverity.MODERATE,
                TECHNICIAN_A_ID, false, null
        );
        checklist(requestedTest, "Kiểm tra rò rỉ", true, true, TECHNICIAN_A_ID);
        repair(requestedTest, TECHNICIAN_A_ID, MaintenanceRepairResult.SUCCESS,
                "Đã thay gioăng", "Chạy kiểm tra sau bảo trì");
        postTest(requestedTest, PostMaintenanceTestStatus.REQUESTED,
                "DEMO-TEST-1001-001", null);

        MaintenanceTicket failedTest = ticket(
                TENANT_A, 102L, "Demo - Post test failed", MaintenanceStatus.IN_PROGRESS,
                MaintenancePriority.HIGH, MaintenanceSeverity.MAJOR,
                TECHNICIAN_B_ID, false, null
        );
        checklist(failedTest, "Kiểm tra an toàn", true, true, TECHNICIAN_B_ID);
        repair(failedTest, TECHNICIAN_B_ID, MaintenanceRepairResult.FAILED,
                "Áp suất vẫn không ổn định", "Cần chẩn đoán lại");
        postTest(failedTest, PostMaintenanceTestStatus.FAILED,
                "DEMO-TEST-1001-002", NOW.minusDays(2));

        MaintenanceTicket completed = ticket(
                TENANT_A, 101L, "Demo - Maintenance completed", MaintenanceStatus.COMPLETED,
                MaintenancePriority.MEDIUM, MaintenanceSeverity.MODERATE,
                TECHNICIAN_A_ID, false, NOW.minusDays(1)
        );
        assignment(completed, null, TECHNICIAN_A_ID, MANAGER_ID,
                MaintenanceAssignmentEventType.ASSIGNED);
        assignment(completed, TECHNICIAN_A_ID, TECHNICIAN_A_ID, TECHNICIAN_A_ID,
                MaintenanceAssignmentEventType.ACKNOWLEDGED);
        checklist(completed, "Kiểm tra an toàn", true, true, TECHNICIAN_A_ID);
        checklist(completed, "Chụp ảnh nghiệm thu", false, true, TECHNICIAN_A_ID);
        repair(completed, TECHNICIAN_A_ID, MaintenanceRepairResult.SUCCESS,
                "Thiết bị hoạt động ổn định", "Thay gioăng và cân chỉnh");
        postTest(completed, PostMaintenanceTestStatus.PASSED,
                "DEMO-TEST-1001-003", NOW.minusDays(1));
        audit(completed, MaintenanceAuditAction.TICKET_CLOSED, MANAGER_ID,
                "status=IN_PROGRESS", "status=COMPLETED", "MAINTENANCE");

        MaintenanceTicket reopened = ticket(
                TENANT_A, 102L, "Demo - Reopened ticket", MaintenanceStatus.IN_PROGRESS,
                MaintenancePriority.HIGH, MaintenanceSeverity.MAJOR,
                TECHNICIAN_B_ID, false, null
        );
        checklist(reopened, "Kiểm tra lần trước", true, true, TECHNICIAN_B_ID);
        repair(reopened, TECHNICIAN_B_ID, MaintenanceRepairResult.SUCCESS,
                "Đã sửa ở lần bảo trì trước", "Theo dõi lỗi tái phát");
        postTest(reopened, PostMaintenanceTestStatus.PASSED,
                "DEMO-TEST-1001-004", NOW.minusDays(3));
        audit(reopened, MaintenanceAuditAction.TICKET_CLOSED, MANAGER_ID,
                "status=IN_PROGRESS", "status=COMPLETED", "MAINTENANCE");
        audit(reopened, MaintenanceAuditAction.TICKET_REOPENED, MANAGER_ID,
                "status=COMPLETED", "status=IN_PROGRESS", "MAINTENANCE");

        MaintenanceTicket critical = ticket(
                TENANT_A, 101L, "Demo - Critical temperature alert", MaintenanceStatus.OPEN,
                MaintenancePriority.URGENT, MaintenanceSeverity.CRITICAL,
                null, true, null
        );
        alertLinkRepository.save(MaintenanceAlertLink.builder()
                .tenantId(TENANT_A)
                .sourceAlertId("DEMO-ALERT-TEMP-001")
                .alertType("HIGH_TEMPERATURE")
                .ticketId(critical.getId())
                .machineId(critical.getMachineId())
                .occurredAt(NOW.minusHours(2))
                .build());
        audit(critical, MaintenanceAuditAction.CRITICAL_ALERT_TICKET_CREATED,
                SYSTEM_ACTOR_ID, null, "alertId=DEMO-ALERT-TEMP-001", "CRITICAL_ALERT");
    }

    // Tạo một ticket độc lập cho tenant thứ hai để demo tenant isolation.
    private void seedTenantB() {
        MaintenanceTicket ticket = ticket(
                TENANT_B, 201L, TENANT_B_MARKER, MaintenanceStatus.OPEN,
                MaintenancePriority.LOW, MaintenanceSeverity.MINOR,
                null, false, null
        );
        audit(ticket, MaintenanceAuditAction.TICKET_CREATED, MANAGER_ID,
                null, "status=OPEN", "MAINTENANCE");
    }

    // Lưu ticket sample với các field scalar đúng schema hiện tại.
    private MaintenanceTicket ticket(
            Long tenantId,
            Long machineId,
            String title,
            MaintenanceStatus status,
            MaintenancePriority priority,
            MaintenanceSeverity severity,
            Long technicianId,
            boolean critical,
            LocalDateTime closedAt
    ) {
        MaintenanceTicket ticket = ticketRepository.save(MaintenanceTicket.builder()
                .tenantId(tenantId)
                .machineId(machineId)
                .title(title)
                .description("Dữ liệu mẫu cho demo Maintenance")
                .category("DEMO")
                .severity(severity)
                .priority(priority)
                .status(status)
                .reportedByActorId(MANAGER_ID)
                .assignedToActorId(technicianId)
                .dueAt(NOW.plusDays(7))
                .critical(critical)
                .build());

        if (closedAt != null) {
            ticket.setClosedAt(LocalDateTime.now());
            return ticketRepository.save(ticket);
        }
        return ticket;
    }

    // Lưu một bước lịch sử phân công theo đúng ticket và tenant.
    private void assignment(
            MaintenanceTicket ticket,
            Long previousTechnicianId,
            Long technicianId,
            Long actorId,
            MaintenanceAssignmentEventType type
    ) {
        assignmentRepository.save(MaintenanceAssignmentHistory.builder()
                .tenantId(ticket.getTenantId())
                .ticketId(ticket.getId())
                .previousTechnicianActorId(previousTechnicianId)
                .technicianActorId(technicianId)
                .performedByActorId(actorId)
                .eventType(type)
                .build());
    }

    // Lưu checklist sample với trạng thái hoàn thành nhất quán.
    private void checklist(
            MaintenanceTicket ticket,
            String name,
            boolean required,
            boolean completed,
            Long actorId
    ) {
        MaintenanceChecklistItem item = checklistRepository.save(
                MaintenanceChecklistItem.builder()
                .tenantId(ticket.getTenantId())
                .ticketId(ticket.getId())
                .itemName(name)
                .description("Checklist demo")
                .required(required)
                .completed(completed)
                .resultNote(completed ? "Đã hoàn thành" : null)
                .completedByActorId(actorId)
                .build()
        );
        if (completed) {
            item.setCompletedAt(LocalDateTime.now());
            checklistRepository.save(item);
        }
    }

    // Lưu repair result sample cho ticket đang xét.
    private void repair(
            MaintenanceTicket ticket,
            Long actorId,
            MaintenanceRepairResult result,
            String diagnostic,
            String action
    ) {
        repairRepository.save(MaintenanceRepairLog.builder()
                .tenantId(ticket.getTenantId())
                .ticketId(ticket.getId())
                .diagnosedByActorId(actorId)
                .diagnosticResult(diagnostic)
                .correctiveAction(action)
                .cost(new BigDecimal("125.00"))
                .repairResult(result)
                .note("Repair log demo")
                .build());
    }

    // Lưu post-maintenance test với external id ổn định và duy nhất trong tenant.
    private void postTest(
            MaintenanceTicket ticket,
            PostMaintenanceTestStatus status,
            String externalId,
            LocalDateTime completedAt
    ) {
        PostMaintenanceTest test = postTestRepository.save(
                PostMaintenanceTest.builder()
                .tenantId(ticket.getTenantId())
                .ticketId(ticket.getId())
                .machineId(ticket.getMachineId())
                .requestedByActorId(MANAGER_ID)
                .status(status)
                .externalTestId(externalId)
                .resultMessage(status == PostMaintenanceTestStatus.REQUESTED
                        ? null
                        : "Kết quả demo: " + status)
                .build()
        );
        if (completedAt != null) {
            test.setCompletedAt(LocalDateTime.now());
            postTestRepository.save(test);
        }
    }

    // Lưu audit event scalar phù hợp với trạng thái sample.
    private void audit(
            MaintenanceTicket ticket,
            MaintenanceAuditAction action,
            Long actorId,
            String beforeState,
            String afterState,
            String source
    ) {
        auditRepository.save(MaintenanceAuditEvent.builder()
                .tenantId(ticket.getTenantId())
                .action(action)
                .entityType("TICKET")
                .entityId(ticket.getId())
                .actorId(actorId)
                .ticketId(ticket.getId())
                .machineId(ticket.getMachineId())
                .beforeState(beforeState)
                .afterState(afterState)
                .source(source)
                .build());
    }
}
