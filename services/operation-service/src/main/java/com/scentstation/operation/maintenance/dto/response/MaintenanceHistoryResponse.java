package com.scentstation.operation.maintenance.dto.response;

import com.scentstation.operation.maintenance.enums.MaintenanceAssignmentEventType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class MaintenanceHistoryResponse {

    private MaintenanceTicketResponse ticket;
    private List<AssignmentHistoryResponse> assignmentHistory;
    private List<MaintenanceChecklistItemResponse> checklist;
    private List<MaintenanceRepairLogResponse> repairLogs;
    private List<PostMaintenanceTestResponse> postMaintenanceTests;
    private AlertLinkResponse alertLink;

    @Getter
    @Builder
    public static class AssignmentHistoryResponse {
        private Long id;
        private Long previousTechnicianActorId;
        private Long technicianActorId;
        private Long performedByActorId;
        private MaintenanceAssignmentEventType eventType;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class AlertLinkResponse {
        private String sourceAlertId;
        private String alertType;
        private Long machineId;
        private LocalDateTime occurredAt;
    }
}
