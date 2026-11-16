package com.scentstation.operation.maintenance.mapper;

import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceTicketResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceTicketMapper {

    // Chuyển request tạo phiếu thành entity Maintenance thuộc Operation Service.
    public MaintenanceTicket toEntity(CreateMaintenanceTicketRequest request) {
        return MaintenanceTicket.builder()
                .tenantId(request.getTenantId())
                .machineId(request.getMachineId())
                .locationId(request.getLocationId())
                .slotId(request.getSlotId())
                .bottleId(request.getBottleId())
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .severity(request.getSeverity())
                .priority(request.getPriority())
                .status(MaintenanceStatus.OPEN)
                .reportedByActorId(request.getReportedByActorId())
                .dueAt(request.getDueAt())
                .critical(request.isCritical())
                .build();
    }

    // Chuyển Maintenance entity thành response phục vụ API và lịch sử xử lý.
    public MaintenanceTicketResponse toResponse(MaintenanceTicket ticket) {
        return MaintenanceTicketResponse.builder()
                .id(ticket.getId())
                .tenantId(ticket.getTenantId())
                .machineId(ticket.getMachineId())
                .locationId(ticket.getLocationId())
                .slotId(ticket.getSlotId())
                .bottleId(ticket.getBottleId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .category(ticket.getCategory())
                .severity(ticket.getSeverity())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .reportedByActorId(ticket.getReportedByActorId())
                .assignedToActorId(ticket.getAssignedToActorId())
                .dueAt(ticket.getDueAt())
                .critical(ticket.isCritical())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .closedAt(ticket.getClosedAt())
                .build();
    }
}