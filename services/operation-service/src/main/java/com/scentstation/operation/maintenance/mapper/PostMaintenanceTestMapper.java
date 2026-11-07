package com.scentstation.operation.maintenance.mapper;

import com.scentstation.operation.maintenance.dto.response.PostMaintenanceTestResponse;
import com.scentstation.operation.maintenance.entity.PostMaintenanceTest;
import org.springframework.stereotype.Component;

@Component
public class PostMaintenanceTestMapper {

    // Chuyển kết quả post-maintenance test thành response API.
    public PostMaintenanceTestResponse toResponse(PostMaintenanceTest test) {
        return PostMaintenanceTestResponse.builder()
                .id(test.getId())
                .tenantId(test.getTenantId())
                .ticketId(test.getTicketId())
                .machineId(test.getMachineId())
                .requestedByActorId(test.getRequestedByActorId())
                .status(test.getStatus())
                .externalTestId(test.getExternalTestId())
                .resultMessage(test.getResultMessage())
                .requestedAt(test.getRequestedAt())
                .completedAt(test.getCompletedAt())
                .build();
    }
}
