package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.CloseMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.ReopenMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.UpdateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceTicketResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
import com.scentstation.operation.maintenance.exception.InvalidMaintenanceTicketException;
import com.scentstation.operation.maintenance.exception.MaintenanceNotFoundException;
import com.scentstation.operation.maintenance.mapper.MaintenanceTicketMapper;
import com.scentstation.operation.maintenance.repository.MaintenanceChecklistRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceRepairLogRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import com.scentstation.operation.maintenance.repository.PostMaintenanceTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenanceTicketService {

    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final MaintenanceChecklistRepository maintenanceChecklistRepository;
    private final MaintenanceRepairLogRepository maintenanceRepairLogRepository;
    private final PostMaintenanceTestRepository postMaintenanceTestRepository;
    private final MaintenanceTicketMapper maintenanceTicketMapper;

    // Tạo maintenance ticket mới với trạng thái OPEN mặc định.
    @Transactional
    public MaintenanceTicketResponse createTicket(
            CreateMaintenanceTicketRequest request
    ) {
        MaintenanceTicket ticket =
                maintenanceTicketMapper.toEntity(request);

        MaintenanceTicket saved =
                maintenanceTicketRepository.save(ticket);

        return maintenanceTicketMapper.toResponse(saved);
    }

    // Lấy maintenance ticket theo id trong đúng tenant.
    @Transactional(readOnly = true)
    public MaintenanceTicketResponse getTicketById(
            Long tenantId,
            Long ticketId
    ) {
        MaintenanceTicket ticket =
                findTicketForTenant(tenantId, ticketId);

        return maintenanceTicketMapper.toResponse(ticket);
    }

    // Lấy toàn bộ maintenance ticket của tenant.
    @Transactional(readOnly = true)
    public List<MaintenanceTicketResponse> getTickets(
            Long tenantId
    ) {
        return maintenanceTicketRepository
                .findAllByTenantId(tenantId)
                .stream()
                .map(maintenanceTicketMapper::toResponse)
                .toList();
    }

    // Lọc maintenance ticket của tenant theo trạng thái.
    @Transactional(readOnly = true)
    public List<MaintenanceTicketResponse> getTicketsByStatus(
            Long tenantId,
            MaintenanceStatus status
    ) {
        return maintenanceTicketRepository
                .findAllByTenantIdAndStatus(
                        tenantId,
                        status
                )
                .stream()
                .map(maintenanceTicketMapper::toResponse)
                .toList();
    }

    // Cập nhật thông tin mô tả của ticket nhưng chưa thay đổi workflow trạng thái.
    @Transactional
    public MaintenanceTicketResponse updateTicket(
            Long tenantId,
            Long ticketId,
            UpdateMaintenanceTicketRequest request
    ) {
        MaintenanceTicket ticket =
                findTicketForTenant(tenantId, ticketId);

        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setCategory(request.getCategory());

        if (request.getSeverity() != null) {
            ticket.setSeverity(request.getSeverity());
        }

        if (request.getPriority() != null) {
            ticket.setPriority(request.getPriority());
        }

        ticket.setDueAt(request.getDueAt());

        MaintenanceTicket saved =
                maintenanceTicketRepository.save(ticket);

        return maintenanceTicketMapper.toResponse(saved);
    }

    // Đóng ticket khi checklist, repair result và bài test mới nhất đều hợp lệ.
    @Transactional
    public MaintenanceTicketResponse closeTicket(
            Long ticketId,
            CloseMaintenanceTicketRequest request
    ) {
        MaintenanceTicket ticket = findTicketForTenant(
                request.getTenantId(),
                ticketId
        );

        validateClose(ticket);
        ticket.setStatus(MaintenanceStatus.COMPLETED);
        ticket.setClosedAt(LocalDateTime.now());

        return maintenanceTicketMapper.toResponse(
                maintenanceTicketRepository.save(ticket)
        );
    }

    // Mở lại ticket COMPLETED để tiếp tục xử lý mà không xóa dữ liệu cũ.
    @Transactional
    public MaintenanceTicketResponse reopenTicket(
            Long ticketId,
            ReopenMaintenanceTicketRequest request
    ) {
        MaintenanceTicket ticket = findTicketForTenant(
                request.getTenantId(),
                ticketId
        );

        if (ticket.getStatus() != MaintenanceStatus.COMPLETED) {
            throw new InvalidMaintenanceTicketException(
                    "Only completed maintenance ticket can be reopened"
            );
        }

        ticket.setStatus(
                ticket.getAssignedToActorId() == null
                        ? MaintenanceStatus.OPEN
                        : MaintenanceStatus.IN_PROGRESS
        );
        ticket.setClosedAt(null);

        return maintenanceTicketMapper.toResponse(
                maintenanceTicketRepository.save(ticket)
        );
    }

    // Kiểm tra toàn bộ điều kiện nghiệp vụ trước khi đóng ticket.
    private void validateClose(MaintenanceTicket ticket) {
        if (ticket.getStatus() == MaintenanceStatus.COMPLETED) {
            throw new InvalidMaintenanceTicketException(
                    "Maintenance ticket is already completed"
            );
        }

        if (ticket.getStatus() == MaintenanceStatus.CANCELLED) {
            throw new InvalidMaintenanceTicketException(
                    "Cancelled maintenance ticket cannot be closed"
            );
        }

        if (maintenanceChecklistRepository
                .existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(
                        ticket.getTenantId(),
                        ticket.getId()
                )) {
            throw new InvalidMaintenanceTicketException(
                    "Required maintenance checklist is incomplete"
            );
        }

        if (!maintenanceRepairLogRepository.existsByTenantIdAndTicketId(
                ticket.getTenantId(),
                ticket.getId()
        )) {
            throw new InvalidMaintenanceTicketException(
                    "Maintenance ticket requires a repair result"
            );
        }

        boolean hasPassedTest = postMaintenanceTestRepository
                .existsByTenantIdAndTicketIdAndStatus(
                        ticket.getTenantId(),
                        ticket.getId(),
                        PostMaintenanceTestStatus.PASSED
                );

        if (!hasPassedTest || postMaintenanceTestRepository
                .findFirstByTenantIdAndTicketIdOrderByRequestedAtDescIdDesc(
                        ticket.getTenantId(),
                        ticket.getId()
                )
                .filter(test -> test.getStatus() == PostMaintenanceTestStatus.PASSED)
                .isEmpty()) {
            throw new InvalidMaintenanceTicketException(
                    "Latest post-maintenance test must be PASSED"
            );
        }
    }

    // Tìm ticket trong đúng tenant để ngăn truy cập chéo tenant.
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
                        new MaintenanceNotFoundException(ticketId)
                );
    }
}
