package com.scentstation.order.controller;

import com.scentstation.order.dto.request.CreateOrderRequest;
import com.scentstation.order.dto.request.UpdateOrderStatusRequest;
import com.scentstation.order.dto.response.DispensingResponse;
import com.scentstation.order.dto.response.OrderResponse;
import com.scentstation.order.dto.response.OrderStatusResponse;
import com.scentstation.order.exception.ApiErrorResponse;
import com.scentstation.order.service.DispensingService;
import com.scentstation.order.service.OrderService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Orders", description = "Create and manage customer orders.")
public class OrderController {

    private final OrderService orderService;
    private final DispensingService dispensingService;

    public OrderController(OrderService orderService, DispensingService dispensingService) {
        this.orderService = orderService;
        this.dispensingService = dispensingService;
    }

    @PostMapping
    @Operation(
            summary = "Create an order",
            description = "Creates an order in PENDING state. Submit the example as-is to create a valid order. "
                    + "Product name and price are snapshots supplied by the client; no catalog or database is used.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateOrderRequest.class),
                            examples = @ExampleObject(name = "Valid order", value = """
                                    {
                                      "customerId": 7,
                                      "machineId": "MACHINE-001",
                                      "slotId": "SLOT-01",
                                      "items": [
                                        {
                                          "productId": 3,
                                          "productName": "Scent Sample",
                                          "unitPrice": 12.75,
                                          "quantity": 2
                                        }
                                      ]
                                    }
                                    """)
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created; status is PENDING",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid JSON or validation failed "
                    + "(customerId/productId/quantity must be positive, names must not be blank, "
                    + "unitPrice must be greater than zero, and items must not be empty)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": 400,
                                      "error": "BAD_REQUEST",
                                      "message": "customerId: must be greater than 0",
                                      "path": "/api/orders",
                                      "timestamp": "2026-10-25T10:00:00"
                                    }
                                    """)))
    })
    public ResponseEntity<OrderResponse> createOrder(@Valid @org.springframework.web.bind.annotation.RequestBody
                                                     CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @GetMapping
    @Operation(summary = "List orders", description = "Returns all orders held by this in-memory service instance.")
    @ApiResponse(responseCode = "200", description = "List of orders")
    public ResponseEntity<List<OrderResponse>> getOrders() {
        return ResponseEntity.ok(orderService.getOrders());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details", description = "Returns the order, its item snapshots, total, and dispensing details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order details"),
            @ApiResponse(responseCode = "400", description = "Order ID is not numeric",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order ID does not exist",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get order status", description = "Returns the current order status for the supplied numeric ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current order status"),
            @ApiResponse(responseCode = "400", description = "Order ID is not numeric",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order ID does not exist",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderStatus(id));
    }

    @GetMapping("/{id}/dispensing")
    @Operation(summary = "Get dispensing result",
            description = "Checks the existing dispensing command result after payment. Does not create another command.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispensing result; UNKNOWN may require manual review"),
            @ApiResponse(responseCode = "400", description = "Order ID is not numeric",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order ID does not exist",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "No eligible dispensing command exists",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<DispensingResponse> getDispensingResult(@PathVariable Long id) {
        return ResponseEntity.ok(dispensingService.getDispensingResult(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status",
            description = "Applies an allowed status transition. CONFIRMED, FAILED, EXPIRED, and DISPENSED are controlled by service workflows.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order status updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status or request body",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order ID does not exist",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Status transition is not allowed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<OrderStatusResponse> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request));
    }
}
