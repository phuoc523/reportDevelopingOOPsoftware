package com.scentstation.order.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.order.OrderServiceTestConfiguration;
import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.PaymentCallbackEvent;
import com.scentstation.order.repository.OrderRepository;
import com.scentstation.order.repository.PaymentRepository;
import com.scentstation.order.repository.DispensingRepository;
import com.scentstation.order.repository.TransactionReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OrderServiceTestConfiguration.class)
class PaymentControllerTest {

    private static final String CALLBACK_TOKEN = "local-mock-callback-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DispensingRepository dispensingRepository;

    @Autowired
    private TransactionReviewRepository transactionReviewRepository;

    @BeforeEach
    void clearRepositories() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        dispensingRepository.deleteAll();
        transactionReviewRepository.deleteAll();
    }

    @Test
    void createsOneQrPaymentPerOrder() throws Exception {
        long orderId = createOrder();

        MvcResult firstResult = mockMvc.perform(post("/api/v1/orders/{id}/payments/qr", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.amount").value(25.5))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.qrUrl").value("http://mock-payment.example:3000/"))
                .andExpect(jsonPath("$.qrPayload").value(org.hamcrest.Matchers.containsString("paymentReference")))
                .andReturn();

        JsonNode response = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        String reference = response.get("reference").asText();
        JsonNode qrPayload = objectMapper.readTree(response.get("qrPayload").asText());
        org.junit.jupiter.api.Assertions.assertEquals("SCENTSTATION_PAYMENT", qrPayload.get("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals(reference, qrPayload.get("paymentReference").asText());
        org.junit.jupiter.api.Assertions.assertEquals(2, qrPayload.size());

        mockMvc.perform(post("/api/v1/orders/{id}/payments/qr", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference));
    }

    @Test
    void createsDistinctQrPaymentsForDifferentOrders() throws Exception {
        long firstOrderId = createOrder();
        long secondOrderId = createOrder();

        String firstReference = createPayment(firstOrderId);
        String secondReference = createPayment(secondOrderId);

        org.junit.jupiter.api.Assertions.assertNotEquals(firstReference, secondReference);
        org.junit.jupiter.api.Assertions.assertNotEquals(firstOrderId, secondOrderId);
        JsonNode firstQrData = getQrData(firstReference);
        JsonNode secondQrData = getQrData(secondReference);
        org.junit.jupiter.api.Assertions.assertEquals("SCENTSTATION_PAYMENT", firstQrData.get("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals("SCENTSTATION_PAYMENT", secondQrData.get("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals(firstReference, firstQrData.get("paymentReference").asText());
        org.junit.jupiter.api.Assertions.assertEquals(secondReference, secondQrData.get("paymentReference").asText());
        org.junit.jupiter.api.Assertions.assertNotEquals(
                objectMapper.writeValueAsString(firstQrData),
                objectMapper.writeValueAsString(secondQrData)
        );
    }

    @Test
    void publicPaymentResponseUsesRootUrlForPreviouslyStoredQrPayloads() throws Exception {
        long orderId = createOrder();
        paymentRepository.saveIfAbsent(new Payment(
                "PAY-LEGACY",
                orderId,
                new java.math.BigDecimal("25.50"),
                "VND",
                "http://localhost:3000/mock-payment/PAY-LEGACY",
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(15)
        ));

        mockMvc.perform(post("/api/v1/payments/public/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-LEGACY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.qrUrl").value("http://mock-payment.example:3000/"))
                .andExpect(jsonPath("$.payment.qrPayload").value(org.hamcrest.Matchers.containsString("paymentReference")));
        JsonNode legacyQrData = getQrData("PAY-LEGACY");
        org.junit.jupiter.api.Assertions.assertEquals("SCENTSTATION_PAYMENT", legacyQrData.get("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals("PAY-LEGACY", legacyQrData.get("paymentReference").asText());
    }

    @Test
    void publicLookupAndConfirmResolveTheReferenceFromTheRequestBody() throws Exception {
        long firstOrderId = createOrder();
        long secondOrderId = createOrder();
        String firstReference = createPayment(firstOrderId);
        String secondReference = createPayment(secondOrderId);

        mockMvc.perform(post("/api/v1/payments/public/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"" + firstReference + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.reference").value(firstReference))
                .andExpect(jsonPath("$.order.id").value(firstOrderId))
                .andExpect(jsonPath("$.order.customerId").doesNotExist())
                .andExpect(jsonPath("$.order.tenantId").doesNotExist())
                .andExpect(jsonPath("$.order.machineId").value("10"))
                .andExpect(jsonPath("$.order.slotId").value("20"));
        mockMvc.perform(post("/api/v1/payments/public/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"" + secondReference + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.reference").value(secondReference))
                .andExpect(jsonPath("$.order.id").value(secondOrderId));

        mockMvc.perform(post("/api/v1/payments/public/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("public-confirm-" + firstReference, firstReference, firstOrderId,
                                "25.50", "VND", "PAID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(firstReference))
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/api/v1/payments/{reference}", secondReference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void publicLookupRejectsUnknownReferences() throws Exception {
        mockMvc.perform(post("/api/v1/payments/public/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-NOT-FOUND\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicPaymentReferencePathEndpointsAreNoLongerExposed() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(get("/api/v1/payments/public/{reference}", reference))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/payments/public/{reference}/confirm", reference)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("removed-path-" + reference, reference,
                                orderId, "25.50", "VND", "PAID")))
                .andExpect(status().isNotFound());
    }

    @Test
    void mockPaymentConfirmsOrderAndCanBeLookedUp() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(post("/api/v1/payments/{reference}/simulate", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/payments/{reference}", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(post("/api/v1/payments/public/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"" + reference + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.order.items[0].productName").value("Scent Sample"));

        mockMvc.perform(get("/api/v1/orders/{id}/dispensing", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPENSED"))
                .andExpect(jsonPath("$.manualReviewRequired").value(false));

        mockMvc.perform(get("/api/v1/orders/{id}/status", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPENSED"));

        mockMvc.perform(post("/api/v1/payments/{reference}/simulate", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void verifiesCallbackAndProcessesDuplicateEventOnlyOnce() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);
        String callback = callbackBody("event-1", reference, orderId, "25.50", "VND", "PAID");

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Payment-Callback-Token", CALLBACK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callback))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.currency").value("VND"));
        String commandId = dispensingRepository.findByOrderId(orderId).orElseThrow().getCommandId();

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Payment-Callback-Token", CALLBACK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callback))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        PaymentCallbackEvent event = paymentRepository.findCallbackEvent("event-1").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(PaymentCallbackEvent.Outcome.ACCEPTED, event.outcome());
        org.junit.jupiter.api.Assertions.assertEquals(
                commandId,
                dispensingRepository.findByOrderId(orderId).orElseThrow().getCommandId()
        );

        mockMvc.perform(get("/api/v1/orders/{id}/status", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void completesOrderPaymentStatusAndCallbackDemoFlow() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(get("/api/v1/orders/{id}/status", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Payment-Callback-Token", CALLBACK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("swagger-demo-" + orderId, reference, orderId,
                                "25.50", "VND", "PAID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/api/v1/payments/{reference}", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/api/v1/orders/{id}/status", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void rejectsUnauthenticatedAndMismatchedCallbacks() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(post("/api/v1/payments/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("event-unauth", reference, orderId, "25.50", "VND", "PAID")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Payment-Callback-Token", CALLBACK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("event-wrong-amount", reference, orderId, "1.00", "VND", "PAID")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payment amount does not match the order"));

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Payment-Callback-Token", CALLBACK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("event-wrong-order", reference, orderId + 1, "25.50", "VND", "PAID")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Payment-Callback-Token", CALLBACK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("event-wrong-currency", reference, orderId, "25.50", "USD", "PAID")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/payments/{reference}", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        org.junit.jupiter.api.Assertions.assertTrue(dispensingRepository.findByOrderId(orderId).isEmpty());
    }

    @Test
    void rejectsReusedEventIdWithDifferentPayload() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Payment-Callback-Token", CALLBACK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("event-reused", reference, orderId, "25.50", "VND", "PAID")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Payment-Callback-Token", CALLBACK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("event-reused", reference, orderId, "25.50", "USD", "PAID")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsLateCallbackForExpiredPayment() throws Exception {
        long orderId = createOrder();
        Payment expiredPayment = new Payment(
                "PAY-EXPIRED",
                orderId,
                new java.math.BigDecimal("25.50"),
                Payment.CURRENCY,
                "http://localhost:3000/mock-payment/PAY-EXPIRED",
                LocalDateTime.now().minusMinutes(20),
                LocalDateTime.now().minusMinutes(5)
        );
        paymentRepository.saveIfAbsent(expiredPayment);

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Payment-Callback-Token", CALLBACK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("event-late", "PAY-EXPIRED", orderId, "25.50", "VND", "PAID")))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/payments/{reference}", "PAY-EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        mockMvc.perform(post("/api/v1/payments/public/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-EXPIRED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("EXPIRED"));

        mockMvc.perform(get("/api/v1/orders/{id}/status", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void recordsFailedCallbackAndMarksOrderFailed() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Payment-Callback-Token", CALLBACK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("event-failed", reference, orderId, "25.50", "VND", "FAILED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(get("/api/v1/orders/{id}/status", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(post("/api/v1/orders/{id}/payments/qr", orderId))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsFailedCallbackForAlreadyPaidPayment() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Payment-Callback-Token", CALLBACK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("paid-event", reference, orderId, "25.50", "VND", "PAID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Payment-Callback-Token", CALLBACK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("late-failed-event", reference, orderId, "25.50", "VND", "FAILED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        mockMvc.perform(get("/api/v1/payments/{reference}", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void rejectsPaymentForUnknownOrderAndUnknownReference() throws Exception {
        mockMvc.perform(post("/api/v1/orders/999/payments/qr"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/payments/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void doesNotProcessPaymentForCancelledOrder() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELLED"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments/{reference}/simulate", reference))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/v1/orders/{id}/payments/qr", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void preventsCancellingAnOrderAfterPayment() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(post("/api/v1/payments/{reference}/simulate", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELLED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void reconciliationFlagsMissingPaymentAndAllowsManualResolution() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(post("/api/v1/payments/reconciliation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payments": []}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedTransactions").value(1))
                .andExpect(jsonPath("$.matchedTransactions").value(0))
                .andExpect(jsonPath("$.discrepancyCount").value(1))
                .andExpect(jsonPath("$.discrepancies[0].reference").value(reference))
                .andExpect(jsonPath("$.discrepancies[0].status").value("OPEN"))
                .andExpect(jsonPath("$.discrepancies[0].manualReviewRequired").value(true));

        mockMvc.perform(get("/api/v1/payments/reviews/{reference}", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Payment is missing from reconciliation data"));

        mockMvc.perform(post("/api/v1/payments/reviews/{reference}/resolve", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.manualReviewRequired").value(false));
    }

    @Test
    void reconciliationAcceptsMatchingSimulatedPaymentRecord() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(post("/api/v1/payments/reconciliation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payments": [{
                                    "reference": "%s",
                                    "orderId": %d,
                                    "amount": 25.50,
                                    "currency": "VND",
                                    "status": "PENDING"
                                  }]
                                }
                                """.formatted(reference, orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedTransactions").value(1))
                .andExpect(jsonPath("$.matchedTransactions").value(1))
                .andExpect(jsonPath("$.discrepancyCount").value(0));
    }

    @Test
    void reconciliationFlagsPaymentFieldMismatches() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(post("/api/v1/payments/reconciliation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payments": [{
                                    "reference": "%s",
                                    "orderId": %d,
                                    "amount": 99.00,
                                    "currency": "USD",
                                    "status": "PAID"
                                  }]
                                }
                                """.formatted(reference, orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedTransactions").value(0))
                .andExpect(jsonPath("$.discrepancies[0].reason")
                        .value("amount does not match, currency does not match, status does not match"));
    }

    @Test
    void publicPaymentEndpointReturnsOnlyTheReferencedOrderAndAcceptsMockConfirmation() throws Exception {
        long firstOrderId = createOrder();
        long secondOrderId = createOrder();
        String firstReference = createPayment(firstOrderId);
        String secondReference = createPayment(secondOrderId);

        mockMvc.perform(post("/api/v1/payments/public/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"" + firstReference + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.reference").value(firstReference))
                .andExpect(jsonPath("$.payment.orderId").value(firstOrderId))
                .andExpect(jsonPath("$.order.id").value(firstOrderId))
                .andExpect(jsonPath("$.order.machineId").value("10"))
                .andExpect(jsonPath("$.order.slotId").value("20"))
                .andExpect(jsonPath("$.order.items[0].productName").value("Scent Sample"));

        mockMvc.perform(post("/api/v1/payments/public/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"" + secondReference + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.reference").value(secondReference))
                .andExpect(jsonPath("$.payment.orderId").value(secondOrderId))
                .andExpect(jsonPath("$.order.id").value(secondOrderId));

        mockMvc.perform(post("/api/v1/payments/public/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("public-event-" + firstOrderId, firstReference,
                                firstOrderId, "25.50", "VND", "PAID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.orderId").value(firstOrderId));

        mockMvc.perform(post("/api/v1/payments/public/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"" + secondReference + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("PENDING"))
                .andExpect(jsonPath("$.order.id").value(secondOrderId));
    }

    @Test
    void publishesOpenApiDocumentAndSwaggerUi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ScentStation Order Service API"))
                .andExpect(jsonPath("$.paths['/api/v1/payments/reconciliation']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments/{reference}/simulate'].post.summary")
                        .value("Simulate successful payment"))
                .andExpect(jsonPath("$.paths['/api/v1/payments/{reference}'].get.summary")
                        .value("Get payment"))
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/status'].get.summary")
                        .value("Get order status"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/orders'].post.requestBody.content['application/json']"
                                + ".examples['Valid order'].value.customerId"
                ).value(7))
                .andExpect(jsonPath(
                        "$.components.schemas.OrderItemRequest.properties.quantity.example"
                ).value(2))
                .andExpect(jsonPath("$.components.schemas.CreateOrderRequest.properties.customerId.example")
                        .value(7))
                .andExpect(jsonPath("$.components.schemas.CreateOrderRequest.properties.tenantId.example")
                        .value("1"))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses['201'].description")
                        .value("Order created; status is PENDING"))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses['400'].description")
                        .value(org.hamcrest.Matchers.containsString("unitPrice")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/orders'].post.responses['400'].content['application/json'].schema.$ref"
                ).value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.message.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/v1/payments/callback'].post.parameters[0].name")
                        .value("X-Payment-Callback-Token"))
                .andExpect(jsonPath("$.paths['/api/v1/payments/callback'].post.parameters[0].required")
                        .value(true))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/callback'].post.requestBody.content['application/json']"
                                + ".examples['Successful payment callback'].value.status"
                ).value("PAID"))
                .andExpect(jsonPath("$.paths['/api/v1/payments/callback'].post.responses['401']").exists());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    private long createOrder() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": 7,
                                  "tenantId": "1",
                                  "machineId": "10",
                                  "slotId": "20",
                                  "items": [
                                    {
                                      "productId": 3,
                                      "productName": "Scent Sample",
                                      "unitPrice": 12.75,
                                      "quantity": 2
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asLong();
    }

    private String createPayment(long orderId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders/{id}/payments/qr", orderId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("reference").asText();
    }

    private JsonNode getQrData(String reference) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/payments/{reference}", reference))
                .andExpect(status().isOk())
                .andReturn();
        String encodedPayload = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("qrPayload").asText();
        return objectMapper.readTree(encodedPayload);
    }

    private String callbackBody(
            String eventId,
            String reference,
            long orderId,
            String amount,
            String currency,
            String status
    ) {
        return """
                {
                  "eventId": "%s",
                  "reference": "%s",
                  "orderId": %d,
                  "amount": %s,
                  "currency": "%s",
                  "status": "%s"
                }
                """.formatted(eventId, reference, orderId, amount, currency, status);
    }
}
