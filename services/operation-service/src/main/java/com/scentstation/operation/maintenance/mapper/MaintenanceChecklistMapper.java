package com.scentstation.operation.maintenance.mapper;

import com.scentstation.operation.maintenance.dto.request.CreateChecklistItemRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceChecklistItemResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceChecklistItem;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceChecklistMapper {

    // Chuyển request tạo checklist item thành entity thuộc maintenance ticket.
    public MaintenanceChecklistItem toEntity(
            Long ticketId,
            CreateChecklistItemRequest request
    ) {
        return MaintenanceChecklistItem.builder()
                .tenantId(request.getTenantId())
                .ticketId(ticketId)
                .itemName(request.getItemName())
                .description(request.getDescription())
                .required(request.isRequired())
                .completed(false)
                .build();
    }

    // Chuyển checklist item thành response API.
    public MaintenanceChecklistItemResponse toResponse(
            MaintenanceChecklistItem item
    ) {
        return MaintenanceChecklistItemResponse.builder()
                .id(item.getId())
                .tenantId(item.getTenantId())
                .ticketId(item.getTicketId())
                .itemName(item.getItemName())
                .description(item.getDescription())
                .required(item.isRequired())
                .completed(item.isCompleted())
                .resultNote(item.getResultNote())
                .completedByActorId(item.getCompletedByActorId())
                .completedAt(item.getCompletedAt())
                .createdAt(item.getCreatedAt())
                .build();
    }
}