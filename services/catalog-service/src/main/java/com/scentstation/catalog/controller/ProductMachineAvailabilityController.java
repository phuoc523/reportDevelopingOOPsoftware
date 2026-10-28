package com.scentstation.catalog.controller;

import com.scentstation.catalog.dto.response.ProductMachineAvailabilityResponse;
import com.scentstation.catalog.service.ProductMachineAvailabilityService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog/availability")
@RequiredArgsConstructor
@Validated
public class ProductMachineAvailabilityController {

    private final ProductMachineAvailabilityService availabilityService;

    // Trả về khả năng phục vụ product trên một machine sau khi kết hợp Catalog và Machine.
    @GetMapping("/machines/{machineId}/products/{productId}")
    public ResponseEntity<ProductMachineAvailabilityResponse> getAvailability(
            @PathVariable
            @Positive
            Long machineId,

            @PathVariable
            @Positive
            Long productId,

            @RequestParam
            @Positive
            Long tenantId
    ) {
        return ResponseEntity.ok(
                availabilityService.getAvailability(
                        tenantId,
                        machineId,
                        productId
                )
        );
    }
}