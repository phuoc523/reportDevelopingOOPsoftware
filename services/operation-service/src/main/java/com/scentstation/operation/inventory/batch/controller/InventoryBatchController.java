package com.scentstation.operation.inventory.batch.controller;

import com.scentstation.operation.inventory.batch.dto.request.CreateInventoryBatchRequest;
import com.scentstation.operation.inventory.batch.dto.response.InventoryBatchResponse;
import com.scentstation.operation.inventory.batch.service.InventoryBatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/batches")
@RequiredArgsConstructor
@Validated
public class InventoryBatchController {

    private final InventoryBatchService inventoryBatchService;

    // Tạo inventory batch mới từ request hợp lệ.
    @PostMapping
    public ResponseEntity<InventoryBatchResponse> createBatch(
            @Valid @RequestBody CreateInventoryBatchRequest request
    ) {
        InventoryBatchResponse response =
                inventoryBatchService.createBatch(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Liệt kê batch theo tenant và tùy chọn lọc product.
    @GetMapping
    public ResponseEntity<List<InventoryBatchResponse>> getBatches(
            @RequestParam @Positive Long tenantId,
            @RequestParam(required = false) @Positive Long productId
    ) {
        if (productId != null) {
            return ResponseEntity.ok(
                    inventoryBatchService
                            .getBatchesByTenantAndProduct(
                                    tenantId,
                                    productId
                            )
            );
        }

        return ResponseEntity.ok(
                inventoryBatchService.getBatchesByTenant(tenantId)
        );
    }

    // Trả một batch theo id trong phạm vi tenant.
    @GetMapping("/{id}")
    public ResponseEntity<InventoryBatchResponse> getBatchById(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                inventoryBatchService.getBatchById(
                        tenantId,
                        id
                )
        );
    }
}
