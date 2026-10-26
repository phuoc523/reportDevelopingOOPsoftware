package com.scentstation.operation.inventory.reconciliation.controller;

import com.scentstation.operation.inventory.reconciliation.dto.request.RecordSensorReadingRequest;
import com.scentstation.operation.inventory.reconciliation.dto.response.SensorReconciliationResponse;
import com.scentstation.operation.inventory.reconciliation.service.SensorReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/reconciliations")
@RequiredArgsConstructor
public class SensorReconciliationController {

    private final SensorReconciliationService sensorReconciliationService;

    @PostMapping
    public ResponseEntity<SensorReconciliationResponse> reconcile(
            @Valid @RequestBody RecordSensorReadingRequest request
    ) {
        return ResponseEntity.ok(
                sensorReconciliationService.reconcile(request)
        );
    }
}