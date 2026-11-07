package com.scentstation.order.service;

import com.scentstation.order.dto.request.CreateOrderRequest;
import com.scentstation.order.dto.request.UpdateOrderStatusRequest;
import com.scentstation.order.dto.response.OrderResponse;
import com.scentstation.order.dto.response.OrderStatusResponse;
import com.scentstation.order.entity.DispensingCommand;
import com.scentstation.order.entity.Order;
import com.scentstation.order.entity.OrderStatus;
import com.scentstation.order.entity.PaymentStatus;
import com.scentstation.order.exception.InvalidOrderStatusTransitionException;
import com.scentstation.order.exception.OrderNotFoundException;
import com.scentstation.order.exception.PaidOrderCancellationException;
import com.scentstation.order.mapper.OrderMapper;
import com.scentstation.order.repository.DispensingRepository;
import com.scentstation.order.repository.OrderRepository;
import com.scentstation.order.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrderService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(
                    OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.EXPIRED, OrderStatus.FAILED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.DISPENSED, EnumSet.of(OrderStatus.PROCESSING),
            OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.EXPIRED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.FAILED, EnumSet.noneOf(OrderStatus.class)
    );

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DispensingRepository dispensingRepository;
    private final OrderMapper orderMapper;
    private final Duration orderExpiration;

    public OrderService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            DispensingRepository dispensingRepository,
            OrderMapper orderMapper,
            @Value("${order.expiration:PT15M}") Duration orderExpiration
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.dispensingRepository = dispensingRepository;
        this.orderMapper = orderMapper;
        if (orderExpiration.isZero() || orderExpiration.isNegative()) {
            throw new IllegalArgumentException("Order expiration must be positive");
        }
        this.orderExpiration = orderExpiration;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = orderMapper.toEntity(request, orderExpiration);
        return orderMapper.toResponse(orderRepository.save(order), null);
    }

    public List<OrderResponse> getOrders() {
        return orderRepository.findAll()
                .stream()
                .map(this::expireIfNecessary)
                .map(order -> orderMapper.toResponse(order, getDispensingCommand(order.getId())))
                .toList();
    }

    public OrderResponse getOrderById(Long id) {
        return orderMapper.toResponse(findOrderById(id), getDispensingCommand(id));
    }

    public void expireOrderAfterPayment(Long id) {
        Order order = findOrderById(id);
        synchronized (order) {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.EXPIRED);
                orderRepository.save(order);
            }
        }
    }

    public OrderStatusResponse getOrderStatus(Long id) {
        return orderMapper.toStatusResponse(findOrderById(id));
    }

    public OrderStatusResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        Order order = findOrderById(id);
        synchronized (order) {
            OrderStatus currentStatus = order.getStatus();
            OrderStatus requestedStatus = request.status();
            if (requestedStatus == OrderStatus.CONFIRMED
                    || requestedStatus == OrderStatus.EXPIRED
                    || requestedStatus == OrderStatus.FAILED
                    || requestedStatus == OrderStatus.DISPENSED) {
                throw new InvalidOrderStatusTransitionException(currentStatus, requestedStatus);
            }
            if (!ALLOWED_TRANSITIONS.get(currentStatus).contains(requestedStatus)) {
                throw new InvalidOrderStatusTransitionException(currentStatus, requestedStatus);
            }
            if (requestedStatus == OrderStatus.CANCELLED
                    && paymentRepository.findByOrderId(id)
                    .map(payment -> payment.getStatus() == PaymentStatus.PAID)
                    .orElse(false)) {
                throw new PaidOrderCancellationException(id);
            }
            order.setStatus(requestedStatus);
            return orderMapper.toStatusResponse(orderRepository.save(order));
        }
    }

    public void confirmOrderAfterPayment(Long id, Runnable markPaymentPaid) {
        Order order = findOrderById(id);
        synchronized (order) {
            if (order.getStatus() != OrderStatus.CONFIRMED
                    && !ALLOWED_TRANSITIONS.get(order.getStatus()).contains(OrderStatus.CONFIRMED)) {
                throw new InvalidOrderStatusTransitionException(order.getStatus(), OrderStatus.CONFIRMED);
            }
            if (order.getStatus() != OrderStatus.CONFIRMED) {
                order.setStatus(OrderStatus.CONFIRMED);
                orderRepository.save(order);
            }
            markPaymentPaid.run();
        }
    }

    public void failOrderAfterPayment(Long id, Runnable markPaymentFailed) {
        Order order = findOrderById(id);
        synchronized (order) {
            if (order.getStatus() != OrderStatus.PENDING) {
                throw new InvalidOrderStatusTransitionException(order.getStatus(), OrderStatus.FAILED);
            }
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            markPaymentFailed.run();
        }
    }

    private Order findOrderById(Long id) {
        return expireIfNecessary(orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id)));
    }

    private Order expireIfNecessary(Order order) {
        if (order.getStatus() == OrderStatus.PENDING
                && order.getExpiresAt() != null
                && !LocalDateTime.now().isBefore(order.getExpiresAt())) {
            order.setStatus(OrderStatus.EXPIRED);
            return orderRepository.save(order);
        }
        return order;
    }

    private DispensingCommand getDispensingCommand(Long orderId) {
        return dispensingRepository.findByOrderId(orderId).orElse(null);
    }
}
