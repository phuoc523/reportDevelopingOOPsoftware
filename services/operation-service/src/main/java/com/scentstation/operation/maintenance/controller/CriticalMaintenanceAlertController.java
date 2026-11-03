package com.scentstation.operation.maintenance.controller;

import com.scentstation.operation.maintenance.dto.request.CriticalMaintenanceAlertRequest;
import com.scentstation.operation.maintenance.dto.response.CriticalMaintenanceAlertResponse;
import com.scentstation.operation.maintenance.service.CriticalMaintenanceAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/maintenance/alerts")
@RequiredArgsConstructor
@Validated
public class CriticalMaintenanceAlertController {

    private final CriticalMaintenanceAlertService
            criticalMaintenanceAlertService;

    // Nhận critical alert từ module Machine/Alert và tạo maintenance ticket tương ứng.
    @PostMapping("/critical")
    public ResponseEntity<CriticalMaintenanceAlertResponse>
    processCriticalAlert(
            @Valid @RequestBody CriticalMaintenanceAlertRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        criticalMaintenanceAlertService
                                .processCriticalAlert(request)
                );
    }
}