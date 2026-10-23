package com.scentstation.order.service;

import com.scentstation.order.dto.request.PaymentCallbackRequest;
import com.scentstation.order.dto.response.OrderResponse;
import com.scentstation.order.dto.response.PaymentResponse;
import com.scentstation.order.entity.OrderStatus;
import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.PaymentCallbackEvent;
import com.scentstation.order.entity.PaymentStatus;
import com.scentstation.order.exception.InvalidOrderStatusTransitionException;
import com.scentstation.order.exception.PaymentNotAvailableException;
import com.scentstation.order.exception.PaymentNotFoundException;
import com.scentstation.order.exception.PaymentVerificationException;
import com.scentstation.order.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final Duration qrExpiration;
    private final String callbackToken;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderService orderService,
            @Value("${payment.qr-expiration:PT15M}") Duration qrExpiration,
            @Value("${payment.callback-token:local-mock-callback-token}") String callbackToken
    ) {
        if (qrExpiration.isZero() || qrExpiration.isNegative()) {
            throw new IllegalArgumentException("Payment QR expiration must be positive");
        }
        if (callbackToken.isBlank()) {
            throw new IllegalArgumentException("Payment callback token must not be blank");
        }
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.qrExpiration = qrExpiration;
        this.callbackToken = callbackToken;
    }

    public PaymentResponse createOrGetQr(Long orderId) {
        OrderResponse order = orderService.getOrderById(orderId);
        Payment existingPayment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (existingPayment != null) {
            expireIfNecessary(existingPayment);
            if (existingPayment.getStatus() == PaymentStatus.PAID || order.status() == OrderStatus.PENDING) {
                return toResponse(existingPayment);
            }
            throw new PaymentNotAvailableException(orderId);
        }

        if (order.status() != OrderStatus.PENDING) {
            throw new PaymentNotAvailableException(orderId);
        }

        String reference = "PAY-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String qrPayload = "scentstation://pay/" + reference
                + "?orderId=" + orderId
                + "&amount=" + order.totalAmount().toPlainString()
                + "&currency=" + Payment.CURRENCY;
        LocalDateTime createdAt = LocalDateTime.now();
        Payment payment = new Payment(
                reference,
                orderId,
                order.totalAmount(),
                Payment.CURRENCY,
                qrPayload,
                createdAt,
                createdAt.plus(qrExpiration)
        );
        return toResponse(paymentRepository.saveIfAbsent(payment));
    }

    public PaymentResponse getPayment(String reference) {
        Payment payment = findPayment(reference);
        expireIfNecessary(payment);
        return toResponse(payment);
    }

    public PaymentResponse simulatePayment(String reference) {
        Payment payment = findPayment(reference);
        return processCallback(new PaymentCallbackRequest(
                "mock-" + UUID.randomUUID(),
                payment.getReference(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                "PAID"
        ), callbackToken);
    }

    public synchronized PaymentResponse processCallback(PaymentCallbackRequest request, String suppliedToken) {
        verifyCallbackToken(suppliedToken);

        PaymentCallbackEvent previousEvent = paymentRepository.findCallbackEvent(request.eventId()).orElse(null);
        if (previousEvent != null) {
            if (!previousEvent.matches(
                    request.reference(), request.orderId(), request.amount(), request.currency(), request.status())) {
                throw new PaymentVerificationException(
                        HttpStatus.CONFLICT,
                        "eventId has already been used with a different callback payload"
                );
            }
            if (previousEvent.outcome() == PaymentCallbackEvent.Outcome.REJECTED) {
                throw new PaymentVerificationException(
                        HttpStatus.valueOf(previousEvent.errorStatus()),
                        previousEvent.message()
                );
            }
            return toResponse(findPayment(request.reference()));
        }

        Payment payment = paymentRepository.findByReference(request.reference()).orElse(null);
        if (payment == null) {
            throw reject(request, HttpStatus.NOT_FOUND, "Payment reference was not found");
        }
        if (!payment.getOrderId().equals(request.orderId())) {
            throw reject(request, HttpStatus.BAD_REQUEST, "Payment reference does not belong to the supplied order");
        }
        if (payment.getAmount().compareTo(request.amount()) != 0) {
            throw reject(request, HttpStatus.BAD_REQUEST, "Payment amount does not match the order");
        }
        if (!payment.getCurrency().equals(request.currency())) {
            throw reject(request, HttpStatus.BAD_REQUEST, "Payment currency does not match the order");
        }

        expireIfNecessary(payment);
        if (payment.getStatus() == PaymentStatus.EXPIRED || payment.getStatus() == PaymentStatus.FAILED) {
            throw reject(request, HttpStatus.CONFLICT, "Payment is no longer payable");
        }
        if (payment.getStatus() == PaymentStatus.PAID && !request.status().equals("PAID")) {
            throw reject(request, HttpStatus.CONFLICT, "A paid payment cannot be changed to a different status");
        }

        OrderStatus orderStatus = orderService.getOrderStatus(payment.getOrderId()).status();
        if (orderStatus == OrderStatus.CANCELLED) {
            throw reject(request, HttpStatus.CONFLICT, "Payment cannot be processed for a cancelled order");
        }

        synchronized (payment) {
            if (payment.getStatus() == PaymentStatus.PAID) {
                return accept(request, payment);
            }
            if (request.status().equals("FAILED")) {
                try {
                    orderService.failOrderAfterPayment(payment.getOrderId(), payment::markFailed);
                } catch (InvalidOrderStatusTransitionException exception) {
                    throw reject(request, HttpStatus.CONFLICT, "Order is not eligible for payment failure");
                }
                return accept(request, payment);
            }

            try {
                orderService.confirmOrderAfterPayment(payment.getOrderId(), payment::markPaid);
            } catch (InvalidOrderStatusTransitionException exception) {
                throw reject(request, HttpStatus.CONFLICT, "Order is not eligible for payment confirmation");
            }
            return accept(request, payment);
        }
    }

    private Payment findPayment(String reference) {
        return paymentRepository.findByReference(reference)
                .orElseThrow(() -> new PaymentNotFoundException(reference));
    }

    private PaymentResponse toResponse(Payment payment) {
        synchronized (payment) {
            return new PaymentResponse(
                    payment.getReference(),
                    payment.getOrderId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getStatus(),
                    payment.getQrPayload(),
                    payment.getCreatedAt(),
                    payment.getExpiresAt(),
                    payment.getPaidAt()
            );
        }
    }

    private void verifyCallbackToken(String suppliedToken) {
        if (suppliedToken == null || !MessageDigest.isEqual(
                callbackToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new PaymentVerificationException(HttpStatus.UNAUTHORIZED, "Invalid payment callback token");
        }
    }

    private PaymentResponse accept(PaymentCallbackRequest request, Payment payment) {
        paymentRepository.saveCallbackEvent(new PaymentCallbackEvent(
                request.eventId(), request.reference(), request.orderId(), request.amount(),
                request.currency(), request.status(), PaymentCallbackEvent.Outcome.ACCEPTED,
                HttpStatus.OK.value(), "Callback processed", LocalDateTime.now()
        ));
        return toResponse(payment);
    }

    private PaymentVerificationException reject(
            PaymentCallbackRequest request,
            HttpStatus status,
            String message
    ) {
        paymentRepository.saveCallbackEvent(new PaymentCallbackEvent(
                request.eventId(), request.reference(), request.orderId(), request.amount(),
                request.currency(), request.status(), PaymentCallbackEvent.Outcome.REJECTED,
                status.value(), message, LocalDateTime.now()
        ));
        return new PaymentVerificationException(status, message);
    }

    private void expireIfNecessary(Payment payment) {
        synchronized (payment) {
            if (payment.isExpiredAt(LocalDateTime.now())) {
                payment.markExpired();
                orderService.expireOrderAfterPayment(payment.getOrderId());
            }
        }
    }
}
