package com.scentstation.operation.maintenance.dto.response;

import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PostMaintenanceTestResponse {

    private Long id;
    private Long tenantId;
    private Long ticketId;
    private Long machineId;
    private Long requestedByActorId;
    private PostMaintenanceTestStatus status;
    private String externalTestId;
    private String resultMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
}
