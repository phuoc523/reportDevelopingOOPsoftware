package com.scentstation.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.order.OrderServiceTestConfiguration;
import com.scentstation.order.repository.OrderRepository;
import com.scentstation.order.repository.DispensingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OrderServiceTestConfiguration.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DispensingRepository dispensingRepository;

    @BeforeEach
    void clearOrders() {
        orderRepository.deleteAll();
        dispensingRepository.deleteAll();
    }

    @Test
    void createsOrderWithProductSnapshotAndCalculatedTotal() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOrderRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.customerId").value(7))
                .andExpect(jsonPath("$.tenantId").value("1"))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].productId").value(3))
                .andExpect(jsonPath("$.items[0].productName").value("Scent Sample"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(25.5))
                .andExpect(jsonPath("$.totalAmount").value(25.5))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void rejectsClientPriceTampering() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(validOrderRequest().replace("12.75", "1.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingInactiveForeignAndIncompatibleResources() throws Exception {
        assertCreateStatus(request(404, "Missing", "10.00", "10", "20"), 404);
        assertCreateStatus(request(4, "Inactive", "10.00", "10", "20"), 409);
        assertCreateStatus(request(5, "Foreign", "10.00", "10", "20"), 409);
        assertCreateStatus(request(3, "Scent Sample", "12.75", "404", "20"), 404);
        assertCreateStatus(request(3, "Scent Sample", "12.75", "10", "404"), 404);
        assertCreateStatus(request(3, "Scent Sample", "12.75", "10", "22"), 409);
        assertCreateStatus(request(3, "Scent Sample", "12.75", "11", "20"), 409);
        assertCreateStatus(request(3, "Scent Sample", "12.75", "10", "21"), 409);
    }

    @Test
    void storesAuthoritativePromotionalPrice() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(request(6, "Promoted", "9.00", "16", "20")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].unitPrice").value(9.0))
                .andExpect(jsonPath("$.totalAmount").value(18.0));
    }

    @Test
    void retrievesOrderDetailsAndStatus() throws Exception {
        long orderId = createOrder();

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(orderId));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/v1/orders/{id}/status", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void returnsEmptyListWhenThereAreNoOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updatesOrderStatusWhenTransitionIsAllowed() throws Exception {
        long orderId = createOrder();

        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CONFIRMED"}
                                """))
                .andExpect(status().isConflict());

        String paymentResponse = mockMvc.perform(post("/api/v1/orders/{id}/payments/qr", orderId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String reference = objectMapper.readTree(paymentResponse).get("reference").asText();
        mockMvc.perform(post("/api/v1/payments/{reference}/simulate", reference))
                .andExpect(status().isOk());

        for (String nextStatus : new String[]{"PROCESSING", "SHIPPED", "DELIVERED"}) {
            mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    java.util.Map.of("status", nextStatus)
                            )))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(nextStatus));
        }
    }

    @Test
    void rejectsInvalidOrderDataAndStatusTransitions() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": 7, "items": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        mockMvc.perform(post("/api/v1/orders")
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
                                      "quantity": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        long orderId = createOrder();
        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DELIVERED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void returnsNotFoundForUnknownOrder() throws Exception {
        mockMvc.perform(get("/api/v1/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void returnsJsonErrorWhenOrderIdIsNotNumeric() throws Exception {
        mockMvc.perform(get("/api/v1/orders/$($order.id)/status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for 'id': a numeric ID is required"));
    }

    private long createOrder() throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOrderRequest()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String validOrderRequest() {
        return """
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
                """;
    }

    private void assertCreateStatus(String request, int expected) throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().is(expected));
    }

    private String request(long productId, String name, String price, String machineId, String slotId) {
        return """
                {"customerId":7,"tenantId":"1","machineId":"%s","slotId":"%s",
                 "items":[{"productId":%d,"productName":"%s","unitPrice":%s,"quantity":2}]}
                """.formatted(machineId, slotId, productId, name, price);
    }
}
