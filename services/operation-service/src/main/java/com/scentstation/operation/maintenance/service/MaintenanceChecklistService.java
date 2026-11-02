package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.CompleteChecklistItemRequest;
import com.scentstation.operation.maintenance.dto.request.CreateChecklistItemRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceChecklistItemResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceChecklistItem;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.exception.InvalidMaintenanceTicketException;
import com.scentstation.operation.maintenance.exception.MaintenanceChecklistNotFoundException;
import com.scentstation.operation.maintenance.exception.MaintenanceNotFoundException;
import com.scentstation.operation.maintenance.mapper.MaintenanceChecklistMapper;
import com.scentstation.operation.maintenance.repository.MaintenanceChecklistRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenanceChecklistService {

    private final MaintenanceChecklistRepository
            maintenanceChecklistRepository;

    private final MaintenanceTicketRepository
            maintenanceTicketRepository;

    private final MaintenanceChecklistMapper
            maintenanceChecklistMapper;

    // Thêm checklist item vào maintenance ticket đang còn hiệu lực.
    @Transactional
    public MaintenanceChecklistItemResponse createChecklistItem(
            Long ticketId,
            CreateChecklistItemRequest request
    ) {
        MaintenanceTicket ticket =
                findTicketForTenant(
                        request.getTenantId(),
                        ticketId
                );

        validateTicketAllowsChecklistChanges(ticket);

        MaintenanceChecklistItem item =
                maintenanceChecklistMapper.toEntity(
                        ticketId,
                        request
                );

        MaintenanceChecklistItem saved =
                maintenanceChecklistRepository.save(item);

        return maintenanceChecklistMapper
                .toResponse(saved);
    }

    // Đánh dấu checklist item thuộc đúng maintenance ticket đã hoàn thành.
    @Transactional
    public MaintenanceChecklistItemResponse completeChecklistItem(
            Long ticketId,
            Long checklistItemId,
            CompleteChecklistItemRequest request
    ) {
        MaintenanceChecklistItem item =
                maintenanceChecklistRepository
                        .findByIdAndTenantId(
                                checklistItemId,
                                request.getTenantId()
                        )
                        .orElseThrow(() ->
                                new MaintenanceChecklistNotFoundException(
                                        checklistItemId
                                )
                        );

        if (!item.getTicketId().equals(ticketId)) {
            throw new MaintenanceChecklistNotFoundException(
                    checklistItemId
            );
        }

        MaintenanceTicket ticket =
                findTicketForTenant(
                        request.getTenantId(),
                        ticketId
                );

        validateTicketAllowsChecklistChanges(ticket);

        if (item.isCompleted()) {
            throw new InvalidMaintenanceTicketException(
                    "Checklist item is already completed"
            );
        }

        item.setCompleted(true);
        item.setResultNote(
                request.getResultNote()
        );
        item.setCompletedByActorId(
                request.getCompletedByActorId()
        );
        item.setCompletedAt(
                LocalDateTime.now()
        );

        MaintenanceChecklistItem saved =
                maintenanceChecklistRepository.save(item);

        return maintenanceChecklistMapper
                .toResponse(saved);
    }

    // Lấy toàn bộ checklist item của một ticket trong đúng tenant.
    @Transactional(readOnly = true)
    public List<MaintenanceChecklistItemResponse> getChecklist(
            Long tenantId,
            Long ticketId
    ) {
        findTicketForTenant(
                tenantId,
                ticketId
        );

        return maintenanceChecklistRepository
                .findAllByTenantIdAndTicketIdOrderByIdAsc(
                        tenantId,
                        ticketId
                )
                .stream()
                .map(
                        maintenanceChecklistMapper::toResponse
                )
                .toList();
    }

    // Kiểm tra ticket còn checklist bắt buộc chưa hoàn thành hay không.
    @Transactional(readOnly = true)
    public boolean hasIncompleteRequiredChecklist(
            Long tenantId,
            Long ticketId
    ) {
        findTicketForTenant(
                tenantId,
                ticketId
        );

        return maintenanceChecklistRepository
                .existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(
                        tenantId,
                        ticketId
                );
    }

    // Tìm maintenance ticket trong phạm vi tenant.
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

    // Không cho sửa checklist của ticket đã hoàn tất hoặc bị hủy.
    private void validateTicketAllowsChecklistChanges(
            MaintenanceTicket ticket
    ) {
        if (ticket.getStatus()
                == MaintenanceStatus.COMPLETED
                || ticket.getStatus()
                == MaintenanceStatus.CANCELLED) {

            throw new InvalidMaintenanceTicketException(
                    "Checklist cannot be changed for completed or cancelled ticket"
            );
        }
    }
}