package com.scentstation.operation.maintenance.mapper;

import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceRepairLogRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceRepairLogResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceRepairLog;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceRepairLogMapper {

    // Chuyển request chẩn đoán/sửa chữa thành repair log thuộc maintenance ticket.
    public MaintenanceRepairLog toEntity(
            Long ticketId,
            CreateMaintenanceRepairLogRequest request
    ) {
        return MaintenanceRepairLog.builder()
                .tenantId(request.getTenantId())
                .ticketId(ticketId)
                .diagnosedByActorId(
                        request.getDiagnosedByActorId()
                )
                .diagnosticResult(
                        request.getDiagnosticResult()
                )
                .correctiveAction(
                        request.getCorrectiveAction()
                )
                .replacedPart(
                        request.getReplacedPart()
                )
                .cost(
                        request.getCost()
                )
                .repairResult(
                        request.getRepairResult()
                )
                .note(
                        request.getNote()
                )
                .evidenceUrl(
                        request.getEvidenceUrl()
                )
                .build();
    }

    // Chuyển repair log thành response API.
    public MaintenanceRepairLogResponse toResponse(
            MaintenanceRepairLog log
    ) {
        return MaintenanceRepairLogResponse.builder()
                .id(log.getId())
                .tenantId(log.getTenantId())
                .ticketId(log.getTicketId())
                .diagnosedByActorId(
                        log.getDiagnosedByActorId()
                )
                .diagnosticResult(
                        log.getDiagnosticResult()
                )
                .correctiveAction(
                        log.getCorrectiveAction()
                )
                .replacedPart(
                        log.getReplacedPart()
                )
                .cost(
                        log.getCost()
                )
                .repairResult(
                        log.getRepairResult()
                )
                .note(
                        log.getNote()
                )
                .evidenceUrl(
                        log.getEvidenceUrl()
                )
                .createdAt(
                        log.getCreatedAt()
                )
                .build();
    }
}