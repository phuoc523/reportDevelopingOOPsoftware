package com.scentstation.operation.inventory.installation.controller;

import com.scentstation.operation.inventory.installation.dto.request.InstallBottleRequest;
import com.scentstation.operation.inventory.installation.dto.request.RemoveBottleRequest;
import com.scentstation.operation.inventory.installation.dto.request.ReplaceBottleRequest;
import com.scentstation.operation.inventory.installation.dto.response.BottleInstallationResponse;
import com.scentstation.operation.inventory.installation.service.BottleInstallationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/installations")
@RequiredArgsConstructor
public class BottleInstallationController {

    private final BottleInstallationService bottleInstallationService;

    @PostMapping("/install")
    public ResponseEntity<BottleInstallationResponse> installBottle(
            @Valid @RequestBody InstallBottleRequest request
    ) {
        return ResponseEntity.ok(
                bottleInstallationService.installBottle(request)
        );
    }

    @PostMapping("/remove")
    public ResponseEntity<BottleInstallationResponse> removeBottle(
            @Valid @RequestBody RemoveBottleRequest request
    ) {
        return ResponseEntity.ok(
                bottleInstallationService.removeBottle(request)
        );
    }

    @PostMapping("/replace")
    public ResponseEntity<BottleInstallationResponse> replaceBottle(
            @Valid @RequestBody ReplaceBottleRequest request
    ) {
        return ResponseEntity.ok(
                bottleInstallationService.replaceBottle(request)
        );
    }
}