package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceRepairLogRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceRepairLogResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceAuditEvent;
import com.scentstation.operation.maintenance.entity.MaintenanceRepairLog;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenanceAuditAction;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.exception.InvalidMaintenanceTicketException;
import com.scentstation.operation.maintenance.exception.MaintenanceNotFoundException;
import com.scentstation.operation.maintenance.mapper.MaintenanceRepairLogMapper;
import com.scentstation.operation.maintenance.repository.MaintenanceRepairLogRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenanceRepairLogService {

    private final MaintenanceRepairLogRepository
            maintenanceRepairLogRepository;

    private final MaintenanceTicketRepository
            maintenanceTicketRepository;

    private final MaintenanceRepairLogMapper
            maintenanceRepairLogMapper;

    private final MaintenanceAuditService maintenanceAuditService;

    // Ghi repair log cho maintenance ticket đang trong quá trình xử lý.
    @Transactional
    public MaintenanceRepairLogResponse createRepairLog(
            Long ticketId,
            CreateMaintenanceRepairLogRequest request
    ) {
        MaintenanceTicket ticket =
                findTicketForTenant(
                        request.getTenantId(),
                        ticketId
                );

        validateTicketAllowsRepairLog(ticket);

        validateRepairActor(ticket, request.getDiagnosedByActorId());

        MaintenanceRepairLog repairLog =
                maintenanceRepairLogMapper.toEntity(
                        ticketId,
                        request
                );

        MaintenanceRepairLog saved =
                maintenanceRepairLogRepository.save(
                        repairLog
                );

        maintenanceAuditService.record(
                MaintenanceAuditEvent.builder()
                        .tenantId(ticket.getTenantId())
                        .action(MaintenanceAuditAction.REPAIR_RECORDED)
                        .entityType("REPAIR_LOG")
                        .entityId(saved.getId())
                        .actorId(saved.getDiagnosedByActorId())
                        .ticketId(ticket.getId())
                        .machineId(ticket.getMachineId())
                        .afterState("result=" + saved.getRepairResult())
                        .source("MAINTENANCE")
                        .build()
        );

        return maintenanceRepairLogMapper
                .toResponse(saved);
    }

    // Lấy toàn bộ repair log của maintenance ticket trong đúng tenant.
    @Transactional(readOnly = true)
    public List<MaintenanceRepairLogResponse> getRepairLogs(
            Long tenantId,
            Long ticketId
    ) {
        findTicketForTenant(
                tenantId,
                ticketId
        );

        return maintenanceRepairLogRepository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAsc(
                        tenantId,
                        ticketId
                )
                .stream()
                .map(
                        maintenanceRepairLogMapper::toResponse
                )
                .toList();
    }

    // Kiểm tra maintenance ticket đã có repair result hay chưa.
    @Transactional(readOnly = true)
    public boolean hasRepairResult(
            Long tenantId,
            Long ticketId
    ) {
        findTicketForTenant(
                tenantId,
                ticketId
        );

        return maintenanceRepairLogRepository
                .existsByTenantIdAndTicketId(
                        tenantId,
                        ticketId
                );
    }

    // Tìm maintenance ticket trong đúng tenant.
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

    // Chỉ cho ghi repair log khi ticket chưa hoàn tất hoặc bị hủy.
    private void validateTicketAllowsRepairLog(
            MaintenanceTicket ticket
    ) {
        if (ticket.getStatus()
                == MaintenanceStatus.COMPLETED
                || ticket.getStatus()
                == MaintenanceStatus.CANCELLED) {

            throw new InvalidMaintenanceTicketException(
                    "Repair log cannot be added to completed or cancelled ticket"
            );
        }
    }

    // Chỉ technician được phân công mới được ghi kết quả sửa chữa khi ticket đã có assignee.
    private void validateRepairActor(
            MaintenanceTicket ticket,
            Long actorId
    ) {
        if (ticket.getAssignedToActorId() != null
                && !ticket.getAssignedToActorId()
                .equals(actorId)) {

            throw new InvalidMaintenanceTicketException(
                    "Repair result can only be recorded by assigned technician"
            );
        }
    }
}
