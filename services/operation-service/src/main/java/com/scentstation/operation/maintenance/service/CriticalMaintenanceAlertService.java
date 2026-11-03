package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.CriticalMaintenanceAlertRequest;
import com.scentstation.operation.maintenance.dto.response.CriticalMaintenanceAlertResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceAuditEvent;
import com.scentstation.operation.maintenance.entity.MaintenanceAlertLink;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenanceAuditAction;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.exception.CriticalAlertAlreadyProcessedException;
import com.scentstation.operation.maintenance.repository.MaintenanceAlertLinkRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CriticalMaintenanceAlertService {

    private final MaintenanceAlertLinkRepository
            maintenanceAlertLinkRepository;

    private final MaintenanceTicketRepository
            maintenanceTicketRepository;

    private final MaintenanceAuditService maintenanceAuditService;

    // Tạo maintenance ticket tự động từ critical alert theo cơ chế idempotent.
    @Transactional
    public CriticalMaintenanceAlertResponse processCriticalAlert(
            CriticalMaintenanceAlertRequest request
    ) {
        validateAlertNotProcessed(request);

        MaintenanceTicket ticket =
                MaintenanceTicket.builder()
                        .tenantId(request.getTenantId())
                        .machineId(request.getMachineId())
                        .locationId(request.getLocationId())
                        .slotId(request.getSlotId())
                        .title(
                                "Critical alert: "
                                        + request.getAlertType()
                        )
                        .description(
                                request.getMessage()
                        )
                        .category(
                                request.getAlertType()
                        )
                        .severity(
                                MaintenanceSeverity.CRITICAL
                        )
                        .priority(
                                MaintenancePriority.URGENT
                        )
                        .status(
                                MaintenanceStatus.OPEN
                        )
                        .reportedByActorId(0L)
                        .critical(true)
                        .build();

        MaintenanceTicket savedTicket =
                maintenanceTicketRepository.save(ticket);

        MaintenanceAlertLink link =
                MaintenanceAlertLink.builder()
                        .tenantId(
                                request.getTenantId()
                        )
                        .sourceAlertId(
                                request.getAlertId()
                        )
                        .alertType(
                                request.getAlertType()
                        )
                        .ticketId(
                                savedTicket.getId()
                        )
                        .machineId(
                                request.getMachineId()
                        )
                        .occurredAt(
                                request.getOccurredAt()
                        )
                        .build();

        saveAlertLink(link, request.getAlertId());

        maintenanceAuditService.record(
                MaintenanceAuditEvent.builder()
                        .tenantId(savedTicket.getTenantId())
                        .action(MaintenanceAuditAction.CRITICAL_ALERT_TICKET_CREATED)
                        .entityType("ALERT")
                        .entityId(link.getId())
                        .actorId(0L)
                        .ticketId(savedTicket.getId())
                        .machineId(savedTicket.getMachineId())
                        .afterState("alertId=" + request.getAlertId())
                        .source("CRITICAL_ALERT")
                        .build()
        );

        return CriticalMaintenanceAlertResponse.builder()
                .alertId(
                        request.getAlertId()
                )
                .ticketId(
                        savedTicket.getId()
                )
                .tenantId(
                        savedTicket.getTenantId()
                )
                .machineId(
                        savedTicket.getMachineId()
                )
                .severity(
                        savedTicket.getSeverity()
                )
                .priority(
                        savedTicket.getPriority()
                )
                .status(
                        savedTicket.getStatus()
                )
                .build();
    }

    // Chuyển lỗi unique do xử lý đồng thời thành lỗi alert đã được xử lý.
    private void saveAlertLink(
            MaintenanceAlertLink link,
            String alertId
    ) {
        try {
            maintenanceAlertLinkRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException exception) {
            throw new CriticalAlertAlreadyProcessedException(alertId);
        }
    }

    // Không cho cùng một alert tạo nhiều maintenance ticket trong một tenant.
    private void validateAlertNotProcessed(
            CriticalMaintenanceAlertRequest request
    ) {
        boolean exists =
                maintenanceAlertLinkRepository
                        .existsByTenantIdAndSourceAlertId(
                                request.getTenantId(),
                                request.getAlertId()
                        );

        if (exists) {
            throw new CriticalAlertAlreadyProcessedException(
                    request.getAlertId()
            );
        }
    }
}
