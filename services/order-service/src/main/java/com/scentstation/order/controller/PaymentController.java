package com.scentstation.order.controller;

import com.scentstation.order.dto.response.PaymentResponse;
import com.scentstation.order.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
