package com.scentstation.order.controller;

import com.scentstation.order.security.OrderCurrentActorContext;
import com.scentstation.order.service.TrialConfirmationService;
import com.scentstation.order.service.TrialConfirmationService.TrialConfirmationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrialConfirmationController {
    public record CreateTrialConfirmationRequest(
            @NotBlank String machineId,
            @NotBlank String slotId,
            @NotNull @Positive Long productId,
            @NotBlank String productName,
            @NotNull @DecimalMin("0.01") BigDecimal unitPrice,
            String currency
    ) {
    }

    public record RespondTrialConfirmationRequest(
            @NotNull Boolean accepted
    ) {
    }

    private final TrialConfirmationService service;
    private final OrderCurrentActorContext actors;

    public TrialConfirmationController(TrialConfirmationService service, OrderCurrentActorContext actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping("/api/v1/orders/trial-confirmations")
    public ResponseEntity<TrialConfirmationResponse> create(
            @Valid @RequestBody CreateTrialConfirmationRequest request) {
        actors.principal();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(
                request.machineId(),
                request.slotId(),
                request.productId(),
                request.productName(),
                request.unitPrice(),
                request.currency()
        ));
    }

    @GetMapping("/api/v1/orders/trial-confirmations/{sessionId}")
    public ResponseEntity<TrialConfirmationResponse> getForKiosk(@PathVariable String sessionId) {
        actors.principal();
        return ResponseEntity.ok(service.get(sessionId));
    }

    @GetMapping("/api/v1/payments/public/trial-confirmations/{sessionId}")
    public ResponseEntity<TrialConfirmationResponse> getPublic(@PathVariable String sessionId) {
        return ResponseEntity.ok(service.get(sessionId));
    }

    @PostMapping("/api/v1/payments/public/trial-confirmations/{sessionId}/respond")
    public ResponseEntity<TrialConfirmationResponse> respondPublic(
            @PathVariable String sessionId,
            @Valid @RequestBody RespondTrialConfirmationRequest request) {
        return ResponseEntity.ok(service.respond(sessionId, Boolean.TRUE.equals(request.accepted())));
    }
}
