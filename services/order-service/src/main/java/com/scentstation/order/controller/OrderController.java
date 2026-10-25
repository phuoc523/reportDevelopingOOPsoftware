package com.scentstation.order.controller;

import com.scentstation.order.dto.request.CreateOrderRequest;
import com.scentstation.order.dto.request.UpdateOrderStatusRequest;
import com.scentstation.order.dto.response.DispensingResponse;
import com.scentstation.order.dto.response.OrderResponse;
import com.scentstation.order.dto.response.OrderStatusResponse;
import com.scentstation.order.service.DispensingService;
import com.scentstation.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final DispensingService dispensingService;

    public OrderController(OrderService orderService, DispensingService dispensingService) {
        this.orderService = orderService;
        this.dispensingService = dispensingService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders() {
        return ResponseEntity.ok(orderService.getOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderStatus(id));
    }

    @GetMapping("/{id}/dispensing")
    public ResponseEntity<DispensingResponse> getDispensingResult(@PathVariable Long id) {
        return ResponseEntity.ok(dispensingService.getDispensingResult(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderStatusResponse> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request));
    }
}
