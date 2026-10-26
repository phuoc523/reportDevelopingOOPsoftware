package com.scentstation.operation.inventory.movement.controller;

import com.scentstation.operation.inventory.movement.dto.response.BottleMovementResponse;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import com.scentstation.operation.inventory.movement.service.BottleMovementService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/movements")
@RequiredArgsConstructor
@Validated
public class BottleMovementController {

    private final BottleMovementService movementService;

    @GetMapping
    public ResponseEntity<List<BottleMovementResponse>>
    getTenantHistory(
            @RequestParam @Positive Long tenantId,
            @RequestParam(required = false)
            BottleMovementType type
    ) {
        if (type != null) {
            return ResponseEntity.ok(
                    movementService.getHistoryByType(
                            tenantId,
                            type
                    )
            );
        }

        return ResponseEntity.ok(
                movementService.getTenantHistory(
                        tenantId
                )
        );
    }

    @GetMapping("/bottles/{bottleId}")
    public ResponseEntity<List<BottleMovementResponse>>
    getBottleHistory(
            @PathVariable @Positive Long bottleId,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                movementService.getBottleHistory(
                        tenantId,
                        bottleId
                )
        );
    }

    @GetMapping("/machines/{machineId}")
    public ResponseEntity<List<BottleMovementResponse>>
    getMachineHistory(
            @PathVariable @Positive Long machineId,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                movementService.getMachineHistory(
                        tenantId,
                        machineId
                )
        );
    }
}