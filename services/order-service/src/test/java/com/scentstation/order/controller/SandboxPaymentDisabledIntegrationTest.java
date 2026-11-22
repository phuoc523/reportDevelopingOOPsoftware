package com.scentstation.order.controller;

import com.scentstation.order.OrderServiceTestConfiguration;
import com.scentstation.order.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "payment.sandbox-mutations-enabled=false")
@AutoConfigureMockMvc
@Import(OrderServiceTestConfiguration.class)
class SandboxPaymentDisabledIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean PaymentService paymentService;

    @Test
    void blocksAllSandboxWritesBeforePaymentProcessing() throws Exception {
        mockMvc.perform(post("/api/v1/payments/callback").header("Authorization", ""))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/api/v1/payments/public/PAY-TEST/confirm").header("Authorization", ""))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/api/v1/payments/PAY-TEST/simulate")
                        .header("Authorization", "Bearer staff-review"))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(paymentService);
    }
}
