package com.scentstation.order.service;

import com.scentstation.order.dto.request.PaymentReconciliationRequest;
import com.scentstation.order.dto.request.SimulatedPaymentRecord;
import com.scentstation.order.dto.response.PaymentReconciliationResponse;
import com.scentstation.order.dto.response.TransactionReviewResponse;
import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.TransactionReview;
import com.scentstation.order.entity.TransactionReviewStatus;
import com.scentstation.order.integration.identity.dto.IdentityPrincipal;
import com.scentstation.order.exception.OrderNotFoundException;
import com.scentstation.order.exception.TransactionReviewNotFoundException;
import com.scentstation.order.repository.PaymentRepository;
import com.scentstation.order.repository.TransactionReviewRepository;
import com.scentstation.order.repository.OrderRepository;
import com.scentstation.order.security.OrderAuthorization;
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
    private final OrderRepository orderRepository;
    private final OrderAuthorization authorization;

    public TransactionReviewService(
            PaymentRepository paymentRepository,
            TransactionReviewRepository reviewRepository,
            OrderRepository orderRepository,
            OrderAuthorization authorization
    ) {
        this.paymentRepository = paymentRepository;
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.authorization = authorization;
    }

    public List<TransactionReviewResponse> getReviews() {
        return reviewRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<TransactionReviewResponse> getReviews(IdentityPrincipal actor) {
        authorization.require(actor, OrderAuthorization.ORDER_REVIEW);
        String tenantId = authorization.tenant(actor);
        return reviewRepository.findAll().stream()
                .filter(review -> orderRepository.findByIdAndTenantId(review.getOrderId(), tenantId).isPresent())
                .map(this::toResponse)
                .toList();
    }

    public TransactionReviewResponse getReview(String reference) {
        return reviewRepository.findByReference(reference)
                .map(this::toResponse)
                .orElseThrow(() -> new TransactionReviewNotFoundException(reference));
    }

    public TransactionReviewResponse getReview(String reference, IdentityPrincipal actor) {
        authorization.require(actor, OrderAuthorization.ORDER_REVIEW);
        TransactionReview review = findReview(reference);
        requireTenantOrder(review.getOrderId(), actor);
        return toResponse(review);
    }

    public TransactionReviewResponse resolveReview(String reference) {
        TransactionReview review = reviewRepository.findByReference(reference)
                .orElseThrow(() -> new TransactionReviewNotFoundException(reference));
        review.resolve();
        reviewRepository.save(review);
        return toResponse(review);
    }

    public TransactionReviewResponse resolveReview(String reference, IdentityPrincipal actor) {
        authorization.require(actor, OrderAuthorization.ORDER_REVIEW);
        TransactionReview review = findReview(reference);
        requireTenantOrder(review.getOrderId(), actor);
        review.resolve();
        reviewRepository.save(review);
        return toResponse(review);
    }

    public PaymentReconciliationResponse reconcile(PaymentReconciliationRequest request) {
        return reconcile(request, paymentRepository.findAll());
    }

    public PaymentReconciliationResponse reconcile(
            PaymentReconciliationRequest request,
            IdentityPrincipal actor
    ) {
        authorization.require(actor, OrderAuthorization.ORDER_REVIEW);
        String tenantId = authorization.tenant(actor);
        request.payments().forEach(record -> requireTenantOrder(record.orderId(), actor));
        List<Payment> tenantPayments = paymentRepository.findAll().stream()
                .filter(payment -> orderRepository.findByIdAndTenantId(payment.getOrderId(), tenantId).isPresent())
                .toList();
        return reconcile(request, tenantPayments);
    }

    private PaymentReconciliationResponse reconcile(
            PaymentReconciliationRequest request,
            List<Payment> expectedPayments
    ) {
        Map<String, SimulatedPaymentRecord> suppliedPayments = new HashMap<>();
        Set<String> duplicateReferences = new HashSet<>();
        for (SimulatedPaymentRecord record : request.payments()) {
            if (suppliedPayments.putIfAbsent(record.reference(), record) != null) {
                duplicateReferences.add(record.reference());
            }
        }

        List<TransactionReviewResponse> discrepancies = new ArrayList<>();
        int matched = 0;
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

    private TransactionReview findReview(String reference) {
        return reviewRepository.findByReference(reference)
                .orElseThrow(() -> new TransactionReviewNotFoundException(reference));
    }

    private void requireTenantOrder(Long orderId, IdentityPrincipal actor) {
        String tenantId = authorization.tenant(actor);
        orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
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
