package com.scentstation.catalog.controller;

import com.scentstation.catalog.dto.response.EffectivePriceResponse;
import com.scentstation.catalog.service.PromotionalPricingService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
@Validated
public class PromotionalPricingController {

    private final PromotionalPricingService promotionalPricingService;

    // Trả về giá hiệu lực hiện tại của một sản phẩm trong tenant.
    @GetMapping("/products/{productId}")
    public ResponseEntity<EffectivePriceResponse> getEffectivePrice(
            @PathVariable
            @Positive
            Long productId,

            @RequestParam
            @Positive
            Long tenantId
    ) {
        return ResponseEntity.ok(
                promotionalPricingService
                        .getEffectivePrice(
                                tenantId,
                                productId
                        )
        );
    }
}