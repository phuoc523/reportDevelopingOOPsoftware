package com.scentstation.operation.maintenance.dto.response;

import com.scentstation.operation.maintenance.enums.MaintenanceRepairResult;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class MaintenanceRepairLogResponse {

    private Long id;

    private Long tenantId;

    private Long ticketId;

    private Long diagnosedByActorId;

    private String diagnosticResult;

    private String correctiveAction;

    private String replacedPart;

    private BigDecimal cost;

    private MaintenanceRepairResult repairResult;

    private String note;

    private String evidenceUrl;

    private LocalDateTime createdAt;
}