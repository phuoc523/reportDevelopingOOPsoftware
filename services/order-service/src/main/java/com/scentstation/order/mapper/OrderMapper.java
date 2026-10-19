package com.scentstation.order.mapper;

import com.scentstation.order.dto.request.CreateOrderRequest;
import com.scentstation.order.dto.request.OrderItemRequest;
import com.scentstation.order.dto.response.OrderItemResponse;
import com.scentstation.order.dto.response.OrderResponse;
import com.scentstation.order.dto.response.OrderStatusResponse;
import com.scentstation.order.entity.Order;
import com.scentstation.order.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class OrderMapper {

    public Order toEntity(CreateOrderRequest request) {
        List<OrderItem> items = request.items()
                .stream()
                .map(this::toEntity)
                .toList();
        BigDecimal totalAmount = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Order(request.customerId(), items, totalAmount);
    }

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems()
                .stream()
                .map(item -> new OrderItemResponse(
                        item.productId(),
                        item.productName(),
                        item.unitPrice(),
                        item.quantity(),
                        item.lineTotal()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                items,
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public OrderStatusResponse toStatusResponse(Order order) {
        return new OrderStatusResponse(order.getId(), order.getStatus(), order.getUpdatedAt());
    }

    private OrderItem toEntity(OrderItemRequest request) {
        BigDecimal lineTotal = request.unitPrice()
                .multiply(BigDecimal.valueOf(request.quantity()));
        return new OrderItem(
                request.productId(),
                request.productName(),
                request.unitPrice(),
                request.quantity(),
                lineTotal
        );
    }
}
