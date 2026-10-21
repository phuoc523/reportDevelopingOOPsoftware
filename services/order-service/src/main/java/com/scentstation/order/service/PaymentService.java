package com.scentstation.order.service;

import com.scentstation.order.dto.response.OrderResponse;
import com.scentstation.order.dto.response.PaymentResponse;
import com.scentstation.order.entity.OrderStatus;
import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.PaymentStatus;
import com.scentstation.order.exception.PaymentNotAvailableException;
import com.scentstation.order.exception.PaymentNotFoundException;
import com.scentstation.order.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

    public PaymentService(PaymentRepository paymentRepository, OrderService orderService) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
    }

    public PaymentResponse createOrGetQr(Long orderId) {
        OrderResponse order = orderService.getOrderById(orderId);
        Payment existingPayment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (existingPayment != null
                && (existingPayment.getStatus() == PaymentStatus.PAID || order.status() == OrderStatus.PENDING)) {
            return toResponse(existingPayment);
        }

        if (order.status() != OrderStatus.PENDING) {
            throw new PaymentNotAvailableException(orderId);
        }

        String reference = "PAY-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String qrPayload = "scentstation://pay/" + reference
                + "?orderId=" + orderId
                + "&amount=" + order.totalAmount().toPlainString();
        Payment payment = new Payment(reference, orderId, order.totalAmount(), qrPayload);
        return toResponse(paymentRepository.saveIfAbsent(payment));
    }

    public PaymentResponse getPayment(String reference) {
        return toResponse(findPayment(reference));
    }

    public PaymentResponse simulatePayment(String reference) {
        Payment payment = findPayment(reference);
        synchronized (payment) {
            if (payment.getStatus() == PaymentStatus.PAID) {
                return toResponse(payment);
            }

            orderService.confirmOrderAfterPayment(payment.getOrderId(), payment::markPaid);
            return toResponse(payment);
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
                    payment.getStatus(),
                    payment.getQrPayload(),
                    payment.getCreatedAt(),
                    payment.getPaidAt()
            );
        }
    }
}
