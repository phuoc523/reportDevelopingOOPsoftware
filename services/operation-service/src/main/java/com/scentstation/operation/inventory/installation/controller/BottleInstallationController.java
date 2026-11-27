package com.scentstation.operation.inventory.installation.controller;

import com.scentstation.operation.inventory.installation.dto.request.InstallBottleRequest;
import com.scentstation.operation.inventory.installation.dto.request.RemoveBottleRequest;
import com.scentstation.operation.inventory.installation.dto.request.ReplaceBottleRequest;
import com.scentstation.operation.inventory.installation.dto.response.BottleInstallationResponse;
import com.scentstation.operation.inventory.installation.service.BottleInstallationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/installations")
@RequiredArgsConstructor
@Validated
public class BottleInstallationController {

    private final BottleInstallationService bottleInstallationService;

    // Trả danh sách các chai đang được lắp trên máy/đầu phun.
    @GetMapping
    public ResponseEntity<List<BottleInstallationResponse>> listActiveInstallations(
            @RequestParam @Positive Long tenantId,
            @RequestParam(required = false) @Positive Long machineId
    ) {
        return ResponseEntity.ok(
                bottleInstallationService.getActiveInstallations(tenantId, machineId)
        );
    }

    // Lắp bottle vào machine slot.
    @PostMapping("/install")
    public ResponseEntity<BottleInstallationResponse> installBottle(
            @Valid @RequestBody InstallBottleRequest request
    ) {
        return ResponseEntity.ok(
                bottleInstallationService.installBottle(request)
        );
    }

    // Tháo bottle khỏi installation active.
    @PostMapping("/remove")
    public ResponseEntity<BottleInstallationResponse> removeBottle(
            @Valid @RequestBody RemoveBottleRequest request
    ) {
        return ResponseEntity.ok(
                bottleInstallationService.removeBottle(request)
        );
    }

    // Thay bottle mới tại vị trí của bottle cũ.
    @PostMapping("/replace")
    public ResponseEntity<BottleInstallationResponse> replaceBottle(
            @Valid @RequestBody ReplaceBottleRequest request
    ) {
        return ResponseEntity.ok(
                bottleInstallationService.replaceBottle(request)
        );
    }
}
