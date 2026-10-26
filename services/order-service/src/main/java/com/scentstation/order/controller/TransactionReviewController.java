package com.scentstation.order.controller;

import com.scentstation.order.dto.request.PaymentReconciliationRequest;
import com.scentstation.order.dto.response.PaymentReconciliationResponse;
import com.scentstation.order.dto.response.TransactionReviewResponse;
import com.scentstation.order.service.TransactionReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class TransactionReviewController {

    private final TransactionReviewService reviewService;

    public TransactionReviewController(TransactionReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/reviews")
    public ResponseEntity<List<TransactionReviewResponse>> getReviews() {
        return ResponseEntity.ok(reviewService.getReviews());
    }

    @GetMapping("/reviews/{reference}")
    public ResponseEntity<TransactionReviewResponse> getReview(@PathVariable String reference) {
        return ResponseEntity.ok(reviewService.getReview(reference));
    }

    @PostMapping("/reviews/{reference}/resolve")
    public ResponseEntity<TransactionReviewResponse> resolveReview(@PathVariable String reference) {
        return ResponseEntity.ok(reviewService.resolveReview(reference));
    }

    @PostMapping("/reconciliation")
    public ResponseEntity<PaymentReconciliationResponse> reconcile(
            @Valid @RequestBody PaymentReconciliationRequest request
    ) {
        return ResponseEntity.ok(reviewService.reconcile(request));
    }
}
