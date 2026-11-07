package com.scentstation.operation.maintenance.controller;

import com.scentstation.operation.maintenance.dto.response.MaintenanceHistoryResponse;
import com.scentstation.operation.maintenance.dto.response.MaintenanceMachineHistoryResponse;
import com.scentstation.operation.maintenance.service.MaintenanceHistoryService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
@Validated
public class MaintenanceHistoryController {

    private final MaintenanceHistoryService maintenanceHistoryService;

    // Trả lịch sử tổng hợp của một maintenance ticket trong tenant.
    @GetMapping("/tickets/{ticketId}/history")
    public ResponseEntity<MaintenanceHistoryResponse> getTicketHistory(
            @PathVariable @Positive Long ticketId,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                maintenanceHistoryService.getTicketHistory(tenantId, ticketId)
        );
    }

    // Trả danh sách lịch sử ticket của một machine trong tenant.
    @GetMapping("/machines/{machineId}/history")
    public ResponseEntity<MaintenanceMachineHistoryResponse> getMachineHistory(
            @PathVariable @Positive Long machineId,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                maintenanceHistoryService.getMachineHistory(tenantId, machineId)
        );
    }
}
