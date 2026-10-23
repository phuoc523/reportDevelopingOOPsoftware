package com.scentstation.order.controller;

import com.scentstation.order.dto.request.PaymentCallbackRequest;
import com.scentstation.order.dto.response.PaymentResponse;
import com.scentstation.order.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/orders/{orderId}/payments/qr")
    public ResponseEntity<PaymentResponse> createOrGetQr(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.createOrGetQr(orderId));
    }

    @GetMapping("/api/payments/{reference}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String reference) {
        return ResponseEntity.ok(paymentService.getPayment(reference));
    }

    @PostMapping("/api/payments/{reference}/simulate")
    public ResponseEntity<PaymentResponse> simulatePayment(@PathVariable String reference) {
        return ResponseEntity.ok(paymentService.simulatePayment(reference));
    }

    @PostMapping("/api/payments/callback")
    public ResponseEntity<PaymentResponse> receiveCallback(
            @Valid @RequestBody PaymentCallbackRequest request,
            @RequestHeader(value = "X-Payment-Callback-Token", required = false) String callbackToken
    ) {
        return ResponseEntity.ok(paymentService.processCallback(request, callbackToken));
    }
}
