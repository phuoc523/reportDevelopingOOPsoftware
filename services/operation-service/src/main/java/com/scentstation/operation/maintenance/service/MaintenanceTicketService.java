package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.UpdateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceTicketResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.exception.MaintenanceNotFoundException;
import com.scentstation.operation.maintenance.mapper.MaintenanceTicketMapper;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenanceTicketService {

    private final MaintenanceTicketRepository maintenanceTicketRepository;
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