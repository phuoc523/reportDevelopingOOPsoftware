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
import com.scentstation.order.integration.identity.dto.IdentityPrincipal;
import com.scentstation.order.repository.DispensingRepository;
import com.scentstation.order.repository.OrderRepository;
import com.scentstation.order.repository.PaymentRepository;
import com.scentstation.order.security.OrderAuthorization;
import com.scentstation.order.security.OrderIdentifiers;
import com.scentstation.order.client.CatalogServiceGateway;
import com.scentstation.order.client.CatalogProductSnapshot;
import com.scentstation.order.client.MachineAuthorityGateway;
import com.scentstation.order.client.MachineSlotSnapshot;
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
    private final OrderAuthorization authorization;
    private final CatalogServiceGateway catalog;
    private final MachineAuthorityGateway machines;

    public OrderService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            DispensingRepository dispensingRepository,
            OrderMapper orderMapper,
            OrderAuthorization authorization,
            CatalogServiceGateway catalog,
            MachineAuthorityGateway machines,
            @Value("${order.expiration:PT15M}") Duration orderExpiration
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.dispensingRepository = dispensingRepository;
        this.orderMapper = orderMapper;
        this.authorization = authorization;
        this.catalog = catalog;
        this.machines = machines;
        if (orderExpiration.isZero() || orderExpiration.isNegative()) {
            throw new IllegalArgumentException("Order expiration must be positive");
        }
        this.orderExpiration = orderExpiration;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = orderMapper.toEntity(request, orderExpiration);
        return orderMapper.toResponse(orderRepository.save(order), null);
    }

    public OrderResponse createOrder(CreateOrderRequest request, IdentityPrincipal actor) {
        authorization.require(actor, OrderAuthorization.KIOSK_ORDER);
        String tenantId = authorization.tenant(actor);
        long requestTenantId = OrderIdentifiers.request(request.tenantId(), "tenantId");
        if (actor.tenantId() == null || actor.tenantId() != requestTenantId
                || !actor.id().equals(request.customerId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Order tenant and customer must match the authenticated actor"
            );
        }
        long machineId = OrderIdentifiers.request(request.machineId(), "machineId");
        long slotId = OrderIdentifiers.request(request.slotId(), "slotId");
        MachineSlotSnapshot slot = machines.getSlot(requestTenantId, machineId, slotId);
        if (!slot.machineAvailable() || !slot.slotAvailable()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Machine or slot is unavailable: " + slot.reason());
        }
        List<CatalogProductSnapshot> products = request.items().stream()
                .map(item -> catalog.getProduct(requestTenantId, item.productId()))
                .toList();
        if (products.stream().anyMatch(product -> product.tenantId() != requestTenantId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Catalog product tenant does not match the authenticated tenant");
        }
        if (products.stream().anyMatch(product -> !product.available())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Product is inactive or unavailable");
        }
        if (products.stream().anyMatch(product -> !Long.toString(product.productId()).equals(slot.productId()))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Product is not assigned to the selected slot");
        }
        for (int index = 0; index < request.items().size(); index++) {
            var intent = request.items().get(index);
            var product = products.get(index);
            if (!intent.productName().equals(product.name()) || intent.unitPrice().compareTo(product.unitPrice()) != 0) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Client product snapshot does not match Catalog authority");
            }
        }
        Order order = orderMapper.toAuthoritativeEntity(request, tenantId, actor.id(), products, orderExpiration);
        return orderMapper.toResponse(orderRepository.save(order), null);
    }

    public List<OrderResponse> getOrders() {
        return orderRepository.findAll()
                .stream()
                .map(this::expireIfNecessary)
                .map(order -> orderMapper.toResponse(order, getDispensingCommand(order.getId())))
                .toList();
    }

    public List<OrderResponse> getOrders(IdentityPrincipal actor) {
        String tenantId = authorization.tenant(actor);
        List<Order> orders;
        if (actor.grants().contains(OrderAuthorization.ORDER_READ)) {
            orders = orderRepository.findAllByTenantId(tenantId);
        } else {
            authorization.require(actor, OrderAuthorization.KIOSK_ORDER);
            orders = orderRepository.findAllByTenantIdAndCustomerId(tenantId, actor.id());
        }
        return orders.stream()
                .map(this::expireIfNecessary)
                .map(order -> orderMapper.toResponse(order, getDispensingCommand(order.getId())))
                .toList();
    }

    public OrderResponse getOrderById(Long id) {
        return orderMapper.toResponse(findOrderById(id), getDispensingCommand(id));
    }

    public OrderResponse getOrderById(Long id, IdentityPrincipal actor) {
        Order order = findOrderForActor(id, actor);
        authorization.requireRead(actor, order);
        return orderMapper.toResponse(order, getDispensingCommand(id));
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

    public OrderStatusResponse getOrderStatus(Long id, IdentityPrincipal actor) {
        Order order = findOrderForActor(id, actor);
        authorization.requireRead(actor, order);
        return orderMapper.toStatusResponse(order);
    }

    public OrderStatusResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        return updateOrderStatus(findOrderById(id), request);
    }

    public OrderStatusResponse updateOrderStatus(
            Long id,
            UpdateOrderStatusRequest request,
            IdentityPrincipal actor
    ) {
        Order order = findOrderForActor(id, actor);
        authorization.requireReview(actor, order);
        return updateOrderStatus(order, request);
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

    public Order requireReadableOrder(Long id, IdentityPrincipal actor) {
        Order order = findOrderForActor(id, actor);
        authorization.requireRead(actor, order);
        return order;
    }

    public Order requireReviewableOrder(Long id, IdentityPrincipal actor) {
        Order order = findOrderForActor(id, actor);
        authorization.requireReview(actor, order);
        return order;
    }

    private Order findOrderForActor(Long id, IdentityPrincipal actor) {
        authorization.tenant(actor);
        Order order = expireIfNecessary(orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id)));
        try {
            authorization.requireSameTenant(actor, order);
            return order;
        } catch (org.springframework.web.server.ResponseStatusException exception) {
            throw new OrderNotFoundException(id);
        }
    }

    private OrderStatusResponse updateOrderStatus(Order order, UpdateOrderStatusRequest request) {
        Long id = order.getId();
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
