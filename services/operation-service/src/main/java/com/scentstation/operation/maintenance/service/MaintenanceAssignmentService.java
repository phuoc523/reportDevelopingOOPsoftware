package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.AssignMaintenanceRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceAssignmentResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceAuditEvent;
import com.scentstation.operation.maintenance.entity.MaintenanceAssignmentHistory;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenanceAuditAction;
import com.scentstation.operation.maintenance.enums.MaintenanceAssignmentEventType;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.exception.InvalidMaintenanceTicketException;
import com.scentstation.operation.maintenance.exception.MaintenanceNotFoundException;
import com.scentstation.operation.maintenance.repository.MaintenanceAssignmentHistoryRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaintenanceAssignmentService {

    private final MaintenanceTicketRepository maintenanceTicketRepository;

    private final MaintenanceAssignmentHistoryRepository
            assignmentHistoryRepository;

    private final MaintenanceAuditService maintenanceAuditService;

    // Phân công hoặc phân công lại technician cho maintenance ticket.
    @Transactional
    public MaintenanceAssignmentResponse assignTechnician(
            Long ticketId,
            AssignMaintenanceRequest request
    ) {
        MaintenanceTicket ticket =
                findTicketForTenant(
                        request.getTenantId(),
                        ticketId
                );

        validateTicketCanBeAssigned(ticket);

        Long previousTechnician =
                ticket.getAssignedToActorId();

        if (request.getTechnicianActorId()
                .equals(previousTechnician)) {

            throw new InvalidMaintenanceTicketException(
                    "Technician is already assigned to this ticket"
            );
        }

        MaintenanceAssignmentEventType eventType =
                previousTechnician == null
                        ? MaintenanceAssignmentEventType.ASSIGNED
                        : MaintenanceAssignmentEventType.REASSIGNED;

        ticket.setAssignedToActorId(
                request.getTechnicianActorId()
        );

        ticket.setStatus(
                MaintenanceStatus.ASSIGNED
        );

        maintenanceTicketRepository.save(ticket);

        MaintenanceAssignmentHistory history =
                saveHistory(
                        ticket,
                        previousTechnician,
                        request.getTechnicianActorId(),
                        request.getAssignedByActorId(),
                        eventType
                );

        recordAudit(
                ticket,
                history,
                eventType == MaintenanceAssignmentEventType.ASSIGNED
                        ? MaintenanceAuditAction.TECHNICIAN_ASSIGNED
                        : MaintenanceAuditAction.TECHNICIAN_REASSIGNED,
                request.getAssignedByActorId(),
                "technician=" + previousTechnician,
                "technician=" + request.getTechnicianActorId()
        );

        return toResponse(
                ticket,
                history
        );
    }

    // Ghi nhận technician xác nhận nhận ticket và bắt đầu xử lý.
    @Transactional
    public MaintenanceAssignmentResponse acknowledgeAssignment(
            Long tenantId,
            Long ticketId,
            Long technicianActorId
    ) {
        MaintenanceTicket ticket =
                findTicketForTenant(
                        tenantId,
                        ticketId
                );

        validateTechnicianCanAcknowledge(
                ticket,
                technicianActorId
        );

        ticket.setStatus(
                MaintenanceStatus.IN_PROGRESS
        );

        maintenanceTicketRepository.save(ticket);

        MaintenanceAssignmentHistory history =
                saveHistory(
                        ticket,
                        ticket.getAssignedToActorId(),
                        technicianActorId,
                        technicianActorId,
                        MaintenanceAssignmentEventType.ACKNOWLEDGED
                );

        recordAudit(
                ticket,
                history,
                MaintenanceAuditAction.ASSIGNMENT_ACKNOWLEDGED,
                technicianActorId,
                "status=" + MaintenanceStatus.ASSIGNED,
                "status=" + MaintenanceStatus.IN_PROGRESS
        );

        return toResponse(
                ticket,
                history
        );
    }

    // Tìm ticket trong đúng tenant để ngăn thao tác chéo tenant.
    private MaintenanceTicket findTicketForTenant(
            Long tenantId,
            Long ticketId
    ) {
        return maintenanceTicketRepository
                .findByIdAndTenantId(
                        ticketId,
                        tenantId
                )
                .orElseThrow(() ->
                        new MaintenanceNotFoundException(
                                ticketId
                        )
                );
    }

    // Không cho phân công ticket đã hoàn tất hoặc đã bị hủy.
    private void validateTicketCanBeAssigned(
            MaintenanceTicket ticket
    ) {
        if (ticket.getStatus()
                == MaintenanceStatus.COMPLETED
                || ticket.getStatus()
                == MaintenanceStatus.CANCELLED) {

            throw new InvalidMaintenanceTicketException(
                    "Completed or cancelled ticket cannot be assigned"
            );
        }
    }

    // Chỉ technician đang được phân công mới được xác nhận nhận việc.
    private void validateTechnicianCanAcknowledge(
            MaintenanceTicket ticket,
            Long technicianActorId
    ) {
        if (ticket.getAssignedToActorId() == null
                || !ticket.getAssignedToActorId()
                .equals(technicianActorId)) {

            throw new InvalidMaintenanceTicketException(
                    "Technician is not assigned to this ticket"
            );
        }

        if (ticket.getStatus()
                != MaintenanceStatus.ASSIGNED) {

            throw new InvalidMaintenanceTicketException(
                    "Ticket is not waiting for technician acknowledgement"
            );
        }
    }

    // Lưu lịch sử để mọi lần assign, reassign và acknowledge đều truy vết được.
    private MaintenanceAssignmentHistory saveHistory(
            MaintenanceTicket ticket,
            Long previousTechnicianActorId,
            Long technicianActorId,
            Long performedByActorId,
            MaintenanceAssignmentEventType eventType
    ) {
        MaintenanceAssignmentHistory history =
                MaintenanceAssignmentHistory.builder()
                        .tenantId(ticket.getTenantId())
                        .ticketId(ticket.getId())
                        .previousTechnicianActorId(
                                previousTechnicianActorId
                        )
                        .technicianActorId(
                                technicianActorId
                        )
                        .performedByActorId(
                                performedByActorId
                        )
                        .eventType(eventType)
                        .build();

        return assignmentHistoryRepository
                .save(history);
    }

    // Chuyển kết quả workflow phân công thành response API.
    private MaintenanceAssignmentResponse toResponse(
            MaintenanceTicket ticket,
            MaintenanceAssignmentHistory history
    ) {
        return MaintenanceAssignmentResponse.builder()
                .ticketId(ticket.getId())
                .tenantId(ticket.getTenantId())
                .previousTechnicianActorId(
                        history.getPreviousTechnicianActorId()
                )
                .technicianActorId(
                        history.getTechnicianActorId()
                )
                .performedByActorId(
                        history.getPerformedByActorId()
                )
                .eventType(
                        history.getEventType()
                )
                .ticketStatus(
                        ticket.getStatus()
                )
                .eventTime(
                        history.getCreatedAt()
                )
                .build();
    }

    // Ghi audit cho assign, reassign và acknowledge trong transaction hiện tại.
    private void recordAudit(
            MaintenanceTicket ticket,
            MaintenanceAssignmentHistory history,
            MaintenanceAuditAction action,
            Long actorId,
            String beforeState,
            String afterState
    ) {
        maintenanceAuditService.record(
                MaintenanceAuditEvent.builder()
                        .tenantId(ticket.getTenantId())
                        .action(action)
                        .entityType("ASSIGNMENT")
                        .entityId(history.getId())
                        .actorId(actorId)
                        .ticketId(ticket.getId())
                        .machineId(ticket.getMachineId())
                        .beforeState(beforeState)
                        .afterState(afterState)
                        .source("MAINTENANCE")
                        .build()
        );
    }
}
