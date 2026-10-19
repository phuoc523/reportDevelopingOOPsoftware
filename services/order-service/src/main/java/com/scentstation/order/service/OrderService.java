package com.scentstation.order.service;

import com.scentstation.order.dto.request.CreateOrderRequest;
import com.scentstation.order.dto.request.UpdateOrderStatusRequest;
import com.scentstation.order.dto.response.OrderResponse;
import com.scentstation.order.dto.response.OrderStatusResponse;
import com.scentstation.order.entity.Order;
import com.scentstation.order.entity.OrderStatus;
import com.scentstation.order.exception.InvalidOrderStatusTransitionException;
import com.scentstation.order.exception.OrderNotFoundException;
import com.scentstation.order.mapper.OrderMapper;
import com.scentstation.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrderService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class)
    );

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = orderMapper.toEntity(request);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    public List<OrderResponse> getOrders() {
        return orderRepository.findAll()
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    public OrderResponse getOrderById(Long id) {
        return orderMapper.toResponse(findOrderById(id));
    }

    public OrderStatusResponse getOrderStatus(Long id) {
        return orderMapper.toStatusResponse(findOrderById(id));
    }

    public OrderStatusResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        Order order = findOrderById(id);
        synchronized (order) {
            OrderStatus currentStatus = order.getStatus();
            OrderStatus requestedStatus = request.status();
            if (!ALLOWED_TRANSITIONS.get(currentStatus).contains(requestedStatus)) {
                throw new InvalidOrderStatusTransitionException(currentStatus, requestedStatus);
            }
            order.setStatus(requestedStatus);
            return orderMapper.toStatusResponse(orderRepository.save(order));
        }
    }

    private Order findOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
