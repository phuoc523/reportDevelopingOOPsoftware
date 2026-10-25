package com.scentstation.operation.inventory.bottle.controller;

import com.scentstation.operation.inventory.bottle.dto.request.CreateBottleRequest;
import com.scentstation.operation.inventory.bottle.dto.response.BottleResponse;
import com.scentstation.operation.inventory.bottle.service.BottleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/bottles")
@RequiredArgsConstructor
@Validated
public class BottleController {

    private final BottleService bottleService;

    @PostMapping
    public ResponseEntity<BottleResponse> createBottle(
            @Valid @RequestBody CreateBottleRequest request
    ) {
        BottleResponse response =
                bottleService.createBottle(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<BottleResponse>> getBottles(
            @RequestParam @Positive Long tenantId,
            @RequestParam(required = false) @Positive Long batchId,
            @RequestParam(required = false) @Positive Long productId
    ) {
        if (batchId != null) {
            return ResponseEntity.ok(
                    bottleService.getBottlesByTenantAndBatch(
                            tenantId,
                            batchId
                    )
            );
        }

        if (productId != null) {
            return ResponseEntity.ok(
                    bottleService.getBottlesByTenantAndProduct(
                            tenantId,
                            productId
                    )
            );
        }

        return ResponseEntity.ok(
                bottleService.getBottlesByTenant(tenantId)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<BottleResponse> getBottleById(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                bottleService.getBottleById(
                        tenantId,
                        id
                )
        );
    }
}