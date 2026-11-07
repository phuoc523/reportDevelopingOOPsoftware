package com.scentstation.operation.maintenance.controller;

import com.scentstation.operation.maintenance.dto.request.AcknowledgeMaintenanceRequest;
import com.scentstation.operation.maintenance.dto.request.AssignMaintenanceRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceAssignmentResponse;
import com.scentstation.operation.maintenance.service.MaintenanceAssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/maintenance/tickets")
@RequiredArgsConstructor
@Validated
public class MaintenanceAssignmentController {

    private final MaintenanceAssignmentService maintenanceAssignmentService;

    // Phân công hoặc phân công lại technician cho maintenance ticket.
    @PostMapping("/{ticketId}/assignment")
    public ResponseEntity<MaintenanceAssignmentResponse> assignTechnician(
            @PathVariable @Positive Long ticketId,
            @Valid @RequestBody AssignMaintenanceRequest request
    ) {
        return ResponseEntity.ok(
                maintenanceAssignmentService.assignTechnician(
                        ticketId,
                        request
                )
        );
    }

    // Cho technician đang được phân công xác nhận nhận công việc.
    @PostMapping("/{ticketId}/assignment/acknowledge")
    public ResponseEntity<MaintenanceAssignmentResponse> acknowledgeAssignment(
            @PathVariable @Positive Long ticketId,
            @Valid @RequestBody AcknowledgeMaintenanceRequest request
    ) {
        return ResponseEntity.ok(
                maintenanceAssignmentService.acknowledgeAssignment(
                        request.getTenantId(),
                        ticketId,
                        request.getTechnicianActorId()
                )
        );
    }
}