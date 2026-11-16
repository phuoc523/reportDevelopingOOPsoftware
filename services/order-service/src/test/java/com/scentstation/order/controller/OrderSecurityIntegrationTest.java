package com.scentstation.order.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.order.OrderServiceTestConfiguration;
import com.scentstation.order.repository.DispensingRepository;
import com.scentstation.order.repository.OrderRepository;
import com.scentstation.order.repository.PaymentRepository;
import com.scentstation.order.repository.TransactionReviewRepository;
import com.scentstation.order.entity.Order;
import com.scentstation.order.entity.OrderItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OrderServiceTestConfiguration.class)
class OrderSecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired DispensingRepository dispensingRepository;
    @Autowired TransactionReviewRepository reviewRepository;

    @BeforeEach
    void clearRepositories() {
        paymentRepository.deleteAll();
        dispensingRepository.deleteAll();
        reviewRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void missingAndInvalidBearerAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders").header("Authorization", ""))
                .andExpect(status().isUnauthorized());
        for (String token : new String[]{"invalid", "revoked", "locked", "suspended"}) {
            mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void identityFailuresFailClosed() throws Exception {
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer unavailable"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer malformed"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void spoofedHeadersCannotReplaceMissingPermission() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer no-permissions")
                        .header("X-Tenant-Id", "1")
                        .header("X-User-Id", "7")
                        .header("X-Verified-Permissions", "KIOSK_ORDER,ORDER_REVIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(7, "1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void kioskPermissionAndAuthenticatedIdentityControlCreation() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer staff-read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(20, "1")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(7, "1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("1"))
                .andExpect(jsonPath("$.customerId").value(7));
    }

    @Test
    void requestCannotOverridePrincipalTenantOrCustomer() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(7, "2")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(8, "1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedRequestIdentifiersAreRejected() throws Exception {
        for (String tenantId : new String[]{"001", "TENANT-1", "-1", "0", " 1"}) {
            mockMvc.perform(post("/api/v1/orders")
                            .header("Authorization", "Bearer customer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(orderBody(7, tenantId)))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(7, "1").replace("\"machineId\":\"10\"",
                                "\"machineId\":\"MACHINE-10\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedStoredTenantFailsAsDataIntegrityError() throws Exception {
        Order malformed = new Order("TENANT-1", 7L, "10", "20",
                List.of(new OrderItem(3L, "Scent Sample", BigDecimal.TEN, 1, BigDecimal.TEN)),
                BigDecimal.TEN, "VND", LocalDateTime.now().plusMinutes(15));
        long orderId = orderRepository.save(malformed).getId();

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer staff-read"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void orderReadsRequireStaffPermissionOrCustomerOwnership() throws Exception {
        long orderId = createOrder("customer", 7, "1");

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer no-permissions"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer customer-8"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer customer"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer staff-read"))
                .andExpect(status().isOk());
    }

    @Test
    void tenantCannotReadOrMutateAnotherTenantOrder() throws Exception {
        long orderId = createOrder("customer", 7, "1");

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer tenant-2"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .header("Authorization", "Bearer tenant-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void managementAndReviewActionsRequireOrderReview() throws Exception {
        long orderId = createOrder("customer", 7, "1");

        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .header("Authorization", "Bearer staff-read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/payments/reviews")
                        .header("Authorization", "Bearer staff-read"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .header("Authorization", "Bearer staff-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void paymentUserEndpointsUseStoredOrderOwnership() throws Exception {
        long orderId = createOrder("customer", 7, "1");
        MvcResult qr = mockMvc.perform(post("/api/v1/orders/{id}/payments/qr", orderId)
                        .header("Authorization", "Bearer customer"))
                .andExpect(status().isOk())
                .andReturn();
        String reference = objectMapper.readTree(qr.getResponse().getContentAsString()).get("reference").asText();

        mockMvc.perform(get("/api/v1/payments/{reference}", reference)
                        .header("Authorization", "Bearer customer-8"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/payments/{reference}/simulate", reference)
                        .header("Authorization", "Bearer staff-read"))
                .andExpect(status().isForbidden());
    }

    @Test
    void callbackKeepsSeparateProviderAuthenticationAndNoBearerRequirement() throws Exception {
        long orderId = createOrder("customer", 7, "1");
        MvcResult qr = mockMvc.perform(post("/api/v1/orders/{id}/payments/qr", orderId)
                        .header("Authorization", "Bearer customer"))
                .andExpect(status().isOk()).andReturn();
        JsonNode payment = objectMapper.readTree(qr.getResponse().getContentAsString());
        String callback = """
                {"eventId":"security-callback","reference":"%s","orderId":%d,
                 "amount":25.50,"currency":"VND","status":"PAID"}
                """.formatted(payment.get("reference").asText(), orderId);

        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("Authorization", "")
                        .header("X-Payment-Callback-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(callback))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("Authorization", "")
                        .header("X-Payment-Callback-Token", "local-mock-callback-token")
                        .contentType(MediaType.APPLICATION_JSON).content(callback))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    private long createOrder(String token, long customerId, String tenantId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(customerId, tenantId)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String orderBody(long customerId, String tenantId) {
        return """
                {"customerId":%d,"tenantId":"%s","machineId":"10","slotId":"20",
                 "items":[{"productId":3,"productName":"Scent Sample","unitPrice":12.75,"quantity":2}]}
                """.formatted(customerId, tenantId);
    }
}
