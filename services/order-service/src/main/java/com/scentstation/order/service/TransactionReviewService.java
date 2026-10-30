package com.scentstation.order.service;

import com.scentstation.order.dto.request.PaymentReconciliationRequest;
import com.scentstation.order.dto.request.SimulatedPaymentRecord;
import com.scentstation.order.dto.response.PaymentReconciliationResponse;
import com.scentstation.order.dto.response.TransactionReviewResponse;
import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.TransactionReview;
import com.scentstation.order.entity.TransactionReviewStatus;
import com.scentstation.order.exception.TransactionReviewNotFoundException;
import com.scentstation.order.repository.PaymentRepository;
import com.scentstation.order.repository.TransactionReviewRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TransactionReviewService {

    private final PaymentRepository paymentRepository;
    private final TransactionReviewRepository reviewRepository;

    public TransactionReviewService(
            PaymentRepository paymentRepository,
            TransactionReviewRepository reviewRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.reviewRepository = reviewRepository;
    }

    public List<TransactionReviewResponse> getReviews() {
        return reviewRepository.findAll().stream().map(this::toResponse).toList();
    }

    public TransactionReviewResponse getReview(String reference) {
        return reviewRepository.findByReference(reference)
                .map(this::toResponse)
                .orElseThrow(() -> new TransactionReviewNotFoundException(reference));
    }

    public TransactionReviewResponse resolveReview(String reference) {
        TransactionReview review = reviewRepository.findByReference(reference)
                .orElseThrow(() -> new TransactionReviewNotFoundException(reference));
        review.resolve();
        return toResponse(review);
    }

    public PaymentReconciliationResponse reconcile(PaymentReconciliationRequest request) {
        Map<String, SimulatedPaymentRecord> suppliedPayments = new HashMap<>();
        Set<String> duplicateReferences = new HashSet<>();
        for (SimulatedPaymentRecord record : request.payments()) {
            if (suppliedPayments.putIfAbsent(record.reference(), record) != null) {
                duplicateReferences.add(record.reference());
            }
        }

        List<TransactionReviewResponse> discrepancies = new ArrayList<>();
        int matched = 0;
        List<Payment> expectedPayments = paymentRepository.findAll();
        for (Payment payment : expectedPayments) {
            SimulatedPaymentRecord record = suppliedPayments.remove(payment.getReference());
            String reason;
            if (record == null) {
                reason = "Payment is missing from reconciliation data";
            } else if (duplicateReferences.contains(payment.getReference())) {
                reason = "Duplicate payment reference in reconciliation data";
            } else {
                reason = mismatchReason(payment, record);
            }
            if (reason == null) {
                matched++;
            } else {
                discrepancies.add(toResponse(reviewRepository.openOrUpdate(
                        payment.getReference(), payment.getOrderId(), reason
                )));
            }
        }

        for (SimulatedPaymentRecord unexpected : suppliedPayments.values()) {
            String reason = duplicateReferences.contains(unexpected.reference())
                    ? "Duplicate payment reference in reconciliation data"
                    : "Payment reference is not known to Order Service";
            discrepancies.add(toResponse(reviewRepository.openOrUpdate(
                    unexpected.reference(),
                    unexpected.orderId(),
                    reason
            )));
        }

        return new PaymentReconciliationResponse(
                expectedPayments.size(),
                matched,
                discrepancies.size(),
                List.copyOf(discrepancies)
        );
    }

    private String mismatchReason(Payment payment, SimulatedPaymentRecord record) {
        List<String> mismatches = new ArrayList<>();
        if (!payment.getOrderId().equals(record.orderId())) {
            mismatches.add("orderId does not match");
        }
        if (payment.getAmount().compareTo(record.amount()) != 0) {
            mismatches.add("amount does not match");
        }
        if (!payment.getCurrency().equals(record.currency())) {
            mismatches.add("currency does not match");
        }
        if (!payment.getStatus().name().equals(record.status())) {
            mismatches.add("status does not match");
        }
        return mismatches.isEmpty() ? null : String.join(", ", mismatches);
    }

    private TransactionReviewResponse toResponse(TransactionReview review) {
        boolean manualReviewRequired = review.getStatus() == TransactionReviewStatus.OPEN;
        return new TransactionReviewResponse(
                review.getReference(),
                review.getOrderId(),
                review.getReason(),
                review.getStatus(),
                manualReviewRequired,
                review.getCreatedAt(),
                review.getResolvedAt()
        );
    }
}
