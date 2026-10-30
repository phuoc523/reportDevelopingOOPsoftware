package com.scentstation.order.controller;

import com.scentstation.order.dto.request.PaymentCallbackRequest;
import com.scentstation.order.dto.response.PaymentResponse;
import com.scentstation.order.exception.ApiErrorResponse;
import com.scentstation.order.service.PaymentService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Payments", description = "Mock QR payments, payment callbacks, and status lookup.")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/orders/{orderId}/payments/qr")
    @Operation(summary = "Create or retrieve mock QR payment",
            description = "Call after creating an order. Repeated calls for the same order return its existing payment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment details and QR payload"),
            @ApiResponse(responseCode = "400", description = "Order ID is not numeric",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order does not exist",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Order is not eligible for payment",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PaymentResponse> createOrGetQr(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.createOrGetQr(orderId));
    }

    @GetMapping("/api/payments/{reference}")
    @Operation(summary = "Get payment", description = "Returns the payment status and details for a payment reference.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment details"),
            @ApiResponse(responseCode = "404", description = "Payment reference does not exist",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String reference) {
        return ResponseEntity.ok(paymentService.getPayment(reference));
    }

    @PostMapping("/api/payments/{reference}/simulate")
    @Operation(summary = "Simulate successful payment",
            description = "Development/demo only. Simulates a PAID callback through the same verification flow.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment marked PAID"),
            @ApiResponse(responseCode = "404", description = "Payment reference does not exist",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Payment cannot be processed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PaymentResponse> simulatePayment(@PathVariable String reference) {
        return ResponseEntity.ok(paymentService.simulatePayment(reference));
    }

    @PostMapping("/api/payments/callback")
    @Operation(
            summary = "Receive payment callback",
            description = "Submit the reference, orderId and amount returned by the QR endpoint. "
                    + "In local development, set X-Payment-Callback-Token to local-mock-callback-token "
                    + "(or the configured PAYMENT_CALLBACK_TOKEN). Replace the example reference with the QR "
                    + "response reference and use a unique eventId for each callback.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PaymentCallbackRequest.class),
                            examples = @ExampleObject(name = "Successful payment callback", value = """
                                    {
                                      "eventId": "provider-event-001",
                                      "reference": "PAY-REPLACE-WITH-QR-REFERENCE",
                                      "orderId": 1,
                                      "amount": 25.50,
                                      "currency": "VND",
                                      "status": "PAID"
                                    }
                                    """)
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback verified and payment processed"),
            @ApiResponse(responseCode = "400", description = "Invalid request body or payment details do not match",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid callback token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment reference does not exist",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Payment is expired, cancelled, or already in a conflicting state",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PaymentResponse> receiveCallback(
            @Valid @RequestBody PaymentCallbackRequest request,
            @Parameter(description = "Local default: local-mock-callback-token. Use PAYMENT_CALLBACK_TOKEN when configured.",
                    required = true, example = "local-mock-callback-token")
            @RequestHeader(value = "X-Payment-Callback-Token", required = false) String callbackToken
    ) {
        return ResponseEntity.ok(paymentService.processCallback(request, callbackToken));
    }
}
