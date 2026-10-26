package com.scentstation.operation.inventory.estimation.controller;

import com.scentstation.operation.inventory.estimation.dto.request.UpdateSprayCalibrationRequest;
import com.scentstation.operation.inventory.estimation.dto.response.RemainingSprayResponse;
import com.scentstation.operation.inventory.estimation.service.RemainingSprayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/estimations")
@RequiredArgsConstructor
@Validated
public class RemainingSprayController {

    private final RemainingSprayService remainingSprayService;

    // Cập nhật calibration tiêu thụ cho bottle.
    @PutMapping("/calibration")
    public ResponseEntity<RemainingSprayResponse> updateCalibration(
            @Valid @RequestBody UpdateSprayCalibrationRequest request
    ) {
        return ResponseEntity.ok(
                remainingSprayService.updateCalibration(request)
        );
    }

    // Trả số lần xịt ước tính còn lại của bottle.
    @GetMapping("/bottles/{bottleId}/remaining-sprays")
    public ResponseEntity<RemainingSprayResponse> getRemainingSprays(
            @PathVariable @Positive Long bottleId,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                remainingSprayService.getRemainingSprays(
                        tenantId,
                        bottleId
                )
        );
    }
}
