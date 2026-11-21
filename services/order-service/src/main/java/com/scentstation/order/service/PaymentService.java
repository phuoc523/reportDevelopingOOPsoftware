package com.scentstation.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.order.dto.request.PaymentCallbackRequest;
import com.scentstation.order.dto.response.OrderResponse;
import com.scentstation.order.dto.response.PaymentQrPayload;
import com.scentstation.order.dto.response.PaymentResponse;
import com.scentstation.order.dto.response.PublicOrderDetailsResponse;
import com.scentstation.order.dto.response.PublicPaymentResponse;
import com.scentstation.order.entity.OrderStatus;
import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.PaymentCallbackEvent;
import com.scentstation.order.entity.PaymentStatus;
import com.scentstation.order.integration.identity.dto.IdentityPrincipal;
import com.scentstation.order.exception.InvalidOrderStatusTransitionException;
import com.scentstation.order.exception.PaymentNotAvailableException;
import com.scentstation.order.exception.PaymentNotFoundException;
import com.scentstation.order.exception.PaymentVerificationException;
import com.scentstation.order.repository.PaymentRepository;
import com.scentstation.order.security.OrderAuthorization;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final DispensingService dispensingService;
    private final Duration qrExpiration;
    private final String callbackToken;
    private final String mockPaymentWebBaseUrl;

    public PaymentService(
            PaymentRepository paymentRepository,
            ObjectMapper objectMapper,
            OrderService orderService,
            DispensingService dispensingService,
            @Value("${payment.qr-expiration:PT15M}") Duration qrExpiration,
            @Value("${payment.callback-token:local-mock-callback-token}") String callbackToken,
            @Value("${payment.mock-web-base-url}") String mockPaymentWebBaseUrl
    ) {
        if (qrExpiration.isZero() || qrExpiration.isNegative()) {
            throw new IllegalArgumentException("Payment QR expiration must be positive");
        }
        if (callbackToken.isBlank()) {
            throw new IllegalArgumentException("Payment callback token must not be blank");
        }
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
        this.orderService = orderService;
        this.dispensingService = dispensingService;
        this.qrExpiration = qrExpiration;
        this.callbackToken = callbackToken;
        this.mockPaymentWebBaseUrl = normalizeBaseUrl(mockPaymentWebBaseUrl);
    }

    public PaymentResponse createOrGetQr(Long orderId) {
        OrderResponse order = orderService.getOrderById(orderId);
        if (order.expiresAt() != null && !LocalDateTime.now().isBefore(order.expiresAt())) {
            orderService.expireOrderAfterPayment(orderId);
            throw new PaymentNotAvailableException(orderId);
        }
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
        String qrPayload = encodeQrPayload(reference);
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = createdAt.plus(qrExpiration);
        if (order.expiresAt() != null && expiresAt.isAfter(order.expiresAt())) {
            expiresAt = order.expiresAt();
        }
        Payment payment = new Payment(
                reference,
                orderId,
                order.totalAmount(),
                Payment.CURRENCY,
                qrPayload,
                createdAt,
                expiresAt
        );
        return toResponse(paymentRepository.saveIfAbsent(payment));
    }

    public PaymentResponse createOrGetQr(Long orderId, IdentityPrincipal actor) {
        orderService.requireReadableOrder(orderId, actor);
        return createOrGetQr(orderId);
    }

    public PaymentResponse getPayment(String reference) {
        Payment payment = findPayment(reference);
        expireIfNecessary(payment);
        return toResponse(payment);
    }

    public PublicPaymentResponse getPublicPaymentDetails(String reference) {
        PaymentResponse payment = getPayment(reference);
        OrderResponse order = orderService.getOrderById(payment.orderId());
        return new PublicPaymentResponse(payment, new PublicOrderDetailsResponse(
                order.id(),
                order.items(),
                order.totalAmount(),
                order.status(),
                order.currency(),
                order.expiresAt()
        ));
    }

    @Transactional(noRollbackFor = PaymentVerificationException.class)
    public PaymentResponse confirmPublicPayment(String reference, PaymentCallbackRequest request) {
        if (!reference.equals(request.reference())) {
            throw new PaymentVerificationException(HttpStatus.BAD_REQUEST,
                    "Payment reference must match the callback request.");
        }
        return processCallback(request, callbackToken);
    }

    public PaymentResponse getPayment(String reference, IdentityPrincipal actor) {
        Payment payment = findPayment(reference);
        orderService.requireReadableOrder(payment.getOrderId(), actor);
        expireIfNecessary(payment);
        return toResponse(payment);
    }

    public List<PaymentResponse> getPayments(IdentityPrincipal actor) {
        if (actor == null || !actor.grants().contains(OrderAuthorization.ORDER_READ)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission required: ORDER_READ");
        }
        return paymentRepository.findAll().stream()
                .filter(payment -> canReadPayment(payment, actor))
                .map(payment -> {
                    expireIfNecessary(payment);
                    return toResponse(payment);
                })
                .toList();
    }

    private boolean canReadPayment(Payment payment, IdentityPrincipal actor) {
        try {
            orderService.requireReadableOrder(payment.getOrderId(), actor);
            return true;
        } catch (com.scentstation.order.exception.OrderNotFoundException exception) {
            return false;
        }
    }

    @Transactional(noRollbackFor = PaymentVerificationException.class)
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

    @Transactional(noRollbackFor = PaymentVerificationException.class)
    public PaymentResponse simulatePayment(String reference, IdentityPrincipal actor) {
        Payment payment = findPayment(reference);
        orderService.requireReviewableOrder(payment.getOrderId(), actor);
        return processCallback(new PaymentCallbackRequest(
                "mock-" + UUID.randomUUID(),
                payment.getReference(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                "PAID"
        ), callbackToken);
    }

    @Transactional(noRollbackFor = PaymentVerificationException.class)
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
                    orderService.failOrderAfterPayment(payment.getOrderId(), () -> {
                        payment.markFailed();
                        paymentRepository.save(payment);
                    });
                } catch (InvalidOrderStatusTransitionException exception) {
                    throw reject(request, HttpStatus.CONFLICT, "Order is not eligible for payment failure");
                }
                return accept(request, payment);
            }

            boolean bottleOrder = orderService.fulfillBottleOrderAfterPayment(payment.getOrderId());
            try {
                orderService.confirmOrderAfterPayment(payment.getOrderId(), () -> {
                    payment.markPaid();
                    paymentRepository.save(payment);
                });
            } catch (InvalidOrderStatusTransitionException exception) {
                throw reject(request, HttpStatus.CONFLICT, "Order is not eligible for payment confirmation");
            }
            if (!bottleOrder) dispensingService.requestDispensing(payment.getOrderId());
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
                    mockPaymentWebBaseUrl,
                    encodeQrPayload(payment.getReference()),
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

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Mock payment web base URL must be configured");
        }
        java.net.URI uri = java.net.URI.create(baseUrl.trim());
        String host = uri.getHost();
        if (!"http".equals(uri.getScheme())
                || uri.getHost() == null
                || "localhost".equalsIgnoreCase(host)
                || host.toLowerCase(java.util.Locale.ROOT).endsWith(".localhost")
                || host.matches("(?i)127(?:\\.\\d{1,3}){3}")
                || "0.0.0.0".equals(host)
                || host.matches("(?i)169\\.254(?:\\.\\d{1,3}){2}")
                || "::1".equals(host)
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                || uri.getQuery() != null
                || uri.getFragment() != null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Mock payment web base URL must be an HTTP origin root on a reachable host");
        }
        return uri.getScheme() + "://" + uri.getRawAuthority() + "/";
    }

    private String encodeQrPayload(String reference) {
        try {
            return objectMapper.writeValueAsString(new PaymentQrPayload("SCENTSTATION_PAYMENT", reference));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode payment QR payload", exception);
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
                paymentRepository.save(payment);
                orderService.expireOrderAfterPayment(payment.getOrderId());
            }
        }
    }

}
