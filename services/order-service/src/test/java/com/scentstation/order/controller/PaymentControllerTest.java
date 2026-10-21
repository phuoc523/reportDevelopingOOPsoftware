package com.scentstation.order.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.order.repository.OrderRepository;
import com.scentstation.order.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void clearRepositories() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void createsOneQrPaymentPerOrder() throws Exception {
        long orderId = createOrder();

        MvcResult firstResult = mockMvc.perform(post("/api/orders/{id}/payments/qr", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.amount").value(25.5))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.qrPayload").value(org.hamcrest.Matchers.startsWith(
                        "scentstation://pay/PAY-"
                )))
                .andReturn();

        String reference = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                .get("reference").asText();

        mockMvc.perform(post("/api/orders/{id}/payments/qr", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference));
    }

    @Test
    void mockPaymentConfirmsOrderAndCanBeLookedUp() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(post("/api/payments/{reference}/simulate", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        mockMvc.perform(get("/api/payments/{reference}", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/api/orders/{id}/status", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/payments/{reference}/simulate", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void rejectsPaymentForUnknownOrderAndUnknownReference() throws Exception {
        mockMvc.perform(post("/api/orders/999/payments/qr"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/payments/UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void doesNotProcessPaymentForCancelledOrder() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELLED"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/payments/{reference}/simulate", reference))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/orders/{id}/payments/qr", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void preventsCancellingAnOrderAfterPayment() throws Exception {
        long orderId = createOrder();
        String reference = createPayment(orderId);

        mockMvc.perform(post("/api/payments/{reference}/simulate", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELLED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    private long createOrder() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": 7,
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
        MvcResult result = mockMvc.perform(post("/api/orders/{id}/payments/qr", orderId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("reference").asText();
    }
}
