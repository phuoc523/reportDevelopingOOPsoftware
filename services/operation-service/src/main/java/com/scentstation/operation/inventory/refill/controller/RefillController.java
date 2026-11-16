package com.scentstation.operation.inventory.refill.controller;

import com.scentstation.operation.inventory.refill.dto.request.RefillBottleRequest;
import com.scentstation.operation.inventory.refill.dto.response.RefillBottleResponse;
import com.scentstation.operation.inventory.refill.service.RefillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/refills")
@RequiredArgsConstructor
public class RefillController {

    private final RefillService refillService;

    // Nhận yêu cầu refill bottle và trả kết quả cập nhật tồn kho.
    @PostMapping
    public ResponseEntity<RefillBottleResponse> refillBottle(
            @Valid @RequestBody RefillBottleRequest request
    ) {
        return ResponseEntity.ok(
                refillService.refillBottle(request)
        );
    }
}
