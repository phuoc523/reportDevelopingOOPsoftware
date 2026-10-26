package com.scentstation.operation.inventory.adjustment.controller;

import com.scentstation.operation.inventory.adjustment.dto.request.AdjustInventoryRequest;
import com.scentstation.operation.inventory.adjustment.dto.response.InventoryAdjustmentResponse;
import com.scentstation.operation.inventory.adjustment.service.InventoryAdjustmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/adjustments")
@RequiredArgsConstructor
@Validated
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    // Điều chỉnh lượng bottle theo số đo thực tế được cung cấp.
    @PostMapping
    public ResponseEntity<InventoryAdjustmentResponse> adjust(
            @Valid @RequestBody AdjustInventoryRequest request
    ) {
        return ResponseEntity.ok(
                inventoryAdjustmentService.adjust(request)
        );
    }

    // Trả lịch sử adjustment của một bottle trong tenant.
    @GetMapping("/bottles/{bottleId}")
    public ResponseEntity<List<InventoryAdjustmentResponse>>
    getBottleAdjustmentHistory(
            @PathVariable @Positive Long bottleId,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                inventoryAdjustmentService
                        .getBottleAdjustmentHistory(
                                tenantId,
                                bottleId
                        )
        );
    }
}
