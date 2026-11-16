package com.scentstation.order.mapper;

import com.scentstation.order.dto.request.CreateOrderRequest;
import com.scentstation.order.dto.request.OrderItemRequest;
import com.scentstation.order.dto.response.OrderItemResponse;
import com.scentstation.order.dto.response.OrderResponse;
import com.scentstation.order.dto.response.OrderStatusResponse;
import com.scentstation.order.entity.DispensingCommand;
import com.scentstation.order.entity.DispensingStatus;
import com.scentstation.order.entity.Order;
import com.scentstation.order.entity.OrderItem;
import com.scentstation.order.security.OrderIdentifiers;
import com.scentstation.order.client.CatalogProductSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderMapper {

    public Order toEntity(CreateOrderRequest request, Duration orderExpiration) {
        return toEntity(request,
                OrderIdentifiers.requestText(request.tenantId(), "tenantId"),
                request.customerId(), orderExpiration);
    }

    public Order toEntity(
            CreateOrderRequest request,
            String tenantId,
            Long customerId,
            Duration orderExpiration
    ) {
        List<OrderItem> items = request.items()
                .stream()
                .map(this::toEntity)
                .toList();
        BigDecimal totalAmount = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Order(
                OrderIdentifiers.requestText(tenantId, "tenantId"),
                customerId,
                OrderIdentifiers.requestText(request.machineId(), "machineId"),
                OrderIdentifiers.requestText(request.slotId(), "slotId"),
                items,
                totalAmount,
                "VND",
                LocalDateTime.now().plus(orderExpiration)
        );
    }

    public Order toAuthoritativeEntity(CreateOrderRequest request, String tenantId, Long customerId,
                                       List<CatalogProductSnapshot> products, Duration orderExpiration) {
        List<OrderItem> items = java.util.stream.IntStream.range(0, request.items().size())
                .mapToObj(index -> {
                    OrderItemRequest intent = request.items().get(index);
                    CatalogProductSnapshot product = products.get(index);
                    BigDecimal lineTotal = product.unitPrice().multiply(BigDecimal.valueOf(intent.quantity()));
                    return new OrderItem(product.productId(), product.name(), product.unitPrice(), intent.quantity(), lineTotal);
                }).toList();
        BigDecimal total = items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Order(OrderIdentifiers.requestText(tenantId, "tenantId"), customerId,
                OrderIdentifiers.requestText(request.machineId(), "machineId"),
                OrderIdentifiers.requestText(request.slotId(), "slotId"), items, total, "VND",
                LocalDateTime.now().plus(orderExpiration));
    }

    public OrderResponse toResponse(Order order, DispensingCommand command) {
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
                order.getTenantId(),
                order.getMachineId(),
                order.getSlotId(),
                items,
                order.getTotalAmount(),
                order.getStatus(),
                command == null ? null : command.getCommandId(),
                command == null ? DispensingStatus.NOT_REQUESTED : command.getStatus(),
                command != null && command.isManualReviewRequired(),
                command == null ? null : command.getManualReviewReason(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getCurrency(),
                order.getExpiresAt()
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
