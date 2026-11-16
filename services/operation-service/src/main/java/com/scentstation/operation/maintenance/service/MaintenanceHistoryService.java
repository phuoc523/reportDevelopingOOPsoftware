package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.response.MaintenanceHistoryResponse;
import com.scentstation.operation.maintenance.dto.response.MaintenanceMachineHistoryResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceAlertLink;
import com.scentstation.operation.maintenance.entity.MaintenanceAssignmentHistory;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.exception.MaintenanceNotFoundException;
import com.scentstation.operation.maintenance.mapper.MaintenanceChecklistMapper;
import com.scentstation.operation.maintenance.mapper.MaintenanceRepairLogMapper;
import com.scentstation.operation.maintenance.mapper.MaintenanceTicketMapper;
import com.scentstation.operation.maintenance.mapper.PostMaintenanceTestMapper;
import com.scentstation.operation.maintenance.repository.MaintenanceAlertLinkRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceAssignmentHistoryRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceChecklistRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceRepairLogRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import com.scentstation.operation.maintenance.repository.PostMaintenanceTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaintenanceHistoryService {

    private final MaintenanceTicketRepository ticketRepository;
    private final MaintenanceAssignmentHistoryRepository assignmentRepository;
    private final MaintenanceChecklistRepository checklistRepository;
    private final MaintenanceRepairLogRepository repairLogRepository;
    private final PostMaintenanceTestRepository postTestRepository;
    private final MaintenanceAlertLinkRepository alertLinkRepository;
    private final MaintenanceTicketMapper ticketMapper;
    private final MaintenanceChecklistMapper checklistMapper;
    private final MaintenanceRepairLogMapper repairLogMapper;
    private final PostMaintenanceTestMapper postTestMapper;

    // Tổng hợp lịch sử đầy đủ của một ticket trong đúng tenant.
    @Transactional(readOnly = true)
    public MaintenanceHistoryResponse getTicketHistory(
            Long tenantId,
            Long ticketId
    ) {
        MaintenanceTicket ticket = ticketRepository
                .findByIdAndTenantId(ticketId, tenantId)
                .orElseThrow(() -> new MaintenanceNotFoundException(ticketId));

        return MaintenanceHistoryResponse.builder()
                .ticket(ticketMapper.toResponse(ticket))
                .assignmentHistory(assignmentRepository
                        .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(
                                tenantId, ticketId
                        )
                        .stream()
                        .map(this::toAssignmentResponse)
                        .toList())
                .checklist(checklistRepository
                        .findAllByTenantIdAndTicketIdOrderByIdAsc(tenantId, ticketId)
                        .stream()
                        .map(checklistMapper::toResponse)
                        .toList())
                .repairLogs(repairLogRepository
                        .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(
                                tenantId, ticketId
                        )
                        .stream()
                        .map(repairLogMapper::toResponse)
                        .toList())
                .postMaintenanceTests(postTestRepository
                        .findAllByTenantIdAndTicketIdOrderByRequestedAtAscIdAsc(
                                tenantId, ticketId
                        )
                        .stream()
                        .map(postTestMapper::toResponse)
                        .toList())
                .alertLink(alertLinkRepository
                        .findFirstByTenantIdAndTicketIdOrderByIdAsc(tenantId, ticketId)
                        .map(this::toAlertResponse)
                        .orElse(null))
                .build();
    }

    // Lấy danh sách ticket của machine theo thứ tự mới nhất mà không load nested history.
    @Transactional(readOnly = true)
    public MaintenanceMachineHistoryResponse getMachineHistory(
            Long tenantId,
            Long machineId
    ) {
        return MaintenanceMachineHistoryResponse.builder()
                .tenantId(tenantId)
                .machineId(machineId)
                .tickets(ticketRepository
                        .findAllByTenantIdAndMachineIdOrderByCreatedAtDescIdDesc(
                                tenantId, machineId
                        )
                        .stream()
                        .map(ticketMapper::toResponse)
                        .toList())
                .build();
    }

    // Chuyển assignment entity thành DTO lịch sử độc lập với trạng thái ticket hiện tại.
    private MaintenanceHistoryResponse.AssignmentHistoryResponse
    toAssignmentResponse(MaintenanceAssignmentHistory history) {
        return MaintenanceHistoryResponse.AssignmentHistoryResponse.builder()
                .id(history.getId())
                .previousTechnicianActorId(history.getPreviousTechnicianActorId())
                .technicianActorId(history.getTechnicianActorId())
                .performedByActorId(history.getPerformedByActorId())
                .eventType(history.getEventType())
                .createdAt(history.getCreatedAt())
                .build();
    }

    // Chuyển alert link thành phần nguồn cảnh báo của history response.
    private MaintenanceHistoryResponse.AlertLinkResponse
    toAlertResponse(MaintenanceAlertLink link) {
        return MaintenanceHistoryResponse.AlertLinkResponse.builder()
                .sourceAlertId(link.getSourceAlertId())
                .alertType(link.getAlertType())
                .machineId(link.getMachineId())
                .occurredAt(link.getOccurredAt())
                .build();
    }
}
