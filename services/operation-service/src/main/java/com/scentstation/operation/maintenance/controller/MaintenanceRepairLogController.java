package com.scentstation.operation.maintenance.controller;

import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceRepairLogRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceRepairLogResponse;
import com.scentstation.operation.maintenance.service.MaintenanceRepairLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance/tickets")
@RequiredArgsConstructor
@Validated
public class MaintenanceRepairLogController {

    private final MaintenanceRepairLogService
            maintenanceRepairLogService;

    // Ghi kết quả chẩn đoán và hành động sửa chữa cho maintenance ticket.
    @PostMapping("/{ticketId}/repair-logs")
    public ResponseEntity<MaintenanceRepairLogResponse>
    createRepairLog(
            @PathVariable @Positive Long ticketId,
            @Valid @RequestBody CreateMaintenanceRepairLogRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        maintenanceRepairLogService
                                .createRepairLog(
                                        ticketId,
                                        request
                                )
                );
    }

    // Lấy toàn bộ repair log của một maintenance ticket.
    @GetMapping("/{ticketId}/repair-logs")
    public ResponseEntity<List<MaintenanceRepairLogResponse>>
    getRepairLogs(
            @PathVariable @Positive Long ticketId,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                maintenanceRepairLogService
                        .getRepairLogs(
                                tenantId,
                                ticketId
                        )
        );
    }
}