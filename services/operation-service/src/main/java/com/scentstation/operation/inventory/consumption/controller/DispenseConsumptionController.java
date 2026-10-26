package com.scentstation.operation.inventory.consumption.controller;

import com.scentstation.operation.inventory.consumption.dto.request.RecordDispenseRequest;
import com.scentstation.operation.inventory.consumption.dto.response.DispenseConsumptionResponse;
import com.scentstation.operation.inventory.consumption.service.DispenseConsumptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/consumptions")
@RequiredArgsConstructor
@Validated
public class DispenseConsumptionController {

    private final DispenseConsumptionService dispenseConsumptionService;

    @PostMapping
    public ResponseEntity<DispenseConsumptionResponse> recordDispense(
            @Valid @RequestBody RecordDispenseRequest request
    ) {
        return ResponseEntity.ok(
                dispenseConsumptionService.recordDispense(request)
        );
    }

    @GetMapping("/command/{commandId}")
    public ResponseEntity<DispenseConsumptionResponse> getByCommandId(
            @PathVariable @NotBlank String commandId,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                dispenseConsumptionService.getByCommandId(
                        tenantId,
                        commandId
                )
        );
    }
}