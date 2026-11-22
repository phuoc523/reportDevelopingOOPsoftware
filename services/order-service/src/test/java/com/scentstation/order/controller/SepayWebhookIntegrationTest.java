package com.scentstation.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.order.OrderServiceTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sepay-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "payment.provider=SEPAY", "payment.sepay.account=9999999999", "payment.sepay.holder=TEST HOLDER",
        "payment.sepay.webhook-secret=integration-test-webhook-placeholder"
})
@AutoConfigureMockMvc
@Import(OrderServiceTestConfiguration.class)
@Sql("/database/sepay-credit-events.sql")
class SepayWebhookIntegrationTest {
    private static final String AUTH = "integration-test-webhook-placeholder";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    long orderId;
    String reference;

    @BeforeEach
    void createTrialCheckout() throws Exception {
        jdbc.update("delete from sepay_credit_events");
        orderId = createOrder();
        reference = checkout(orderId);
    }

    private long createOrder() throws Exception {
        String order = mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":7,"tenantId":"1","machineId":"10","slotId":"20",
                                 "items":[{"productId":3,"productName":"Scent Sample","unitPrice":12.75,"quantity":4}]}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(order).get("id").asLong();
    }

    @Test
    void servesVietQrAndOnlyAuthenticatedCreditCanConfirm() throws Exception {
        assertThat(reference).matches("SS[0-9A-F]{20}");
        assertThat(checkout(orderId)).isEqualTo(reference);
        mvc.perform(post("/api/v1/payments/{ref}/simulate", reference)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/public/{ref}", reference)).andExpect(status().isNotFound());
        send("wrong", 1, "in", 51, "9999999999", reference, "FT-1", 401);
        assertThat(outcome(1)).isEmpty();
        send(AUTH, 1, "in", 51, "9999999999", reference, "FT-1", 200);
        send(AUTH, 1, "in", 51, "9999999999", reference, "FT-1", 200);
        assertThat(outcome(1)).containsExactly("PAID");
        assertThat(paymentStatus()).isEqualTo("PAID");
        assertThat(commands()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from dispensing_commands where order_id=?",
                String.class, orderId)).isEqualTo("ACCEPTED");
    }

    @Test
    void rejectsStaleTimestampAndModifiedBodyBeforeParsing() throws Exception {
        byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long current = java.time.Instant.now().getEpochSecond();
        String stale = Long.toString(current - 301);
        mvc.perform(post("/api/v1/webhooks/sepay")
                        .header("X-SePay-Timestamp", stale)
                        .header("X-SePay-Signature", sign(AUTH, stale, body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        String now = Long.toString(current);
        mvc.perform(post("/api/v1/webhooks/sepay")
                        .header("X-SePay-Timestamp", now)
                        .header("X-SePay-Signature", sign(AUTH, now, body))
                        .contentType(MediaType.APPLICATION_JSON).content("{ }"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from sepay_credit_events", Integer.class)).isZero();
    }

    @Test
    void recordsUnmatchedCreditsBeforeAcknowledging() throws Exception {
        send(AUTH, 1, "out", 51, "9999999999", reference, "FT-1", 200);
        send(AUTH, 2, "in", 51, "9999999998", reference, "FT-2", 200);
        send(AUTH, 3, "in", 50, "9999999999", reference, "FT-3", 200);
        send(AUTH, 4, "in", 51, "9999999999", reference + "X", "FT-4", 200);
        assertThat(outcome(1)).containsExactly("UNMATCHED_TRANSFER");
        assertThat(outcome(2)).containsExactly("UNMATCHED_TRANSFER");
        assertThat(outcome(3)).containsExactly("AMOUNT_MISMATCH");
        assertThat(outcome(4)).containsExactly("UNMATCHED_REFERENCE");
        assertThat(paymentStatus()).isEqualTo("PENDING");
        assertThat(commands()).isZero();
    }

    @Test
    void concurrentCreditsAndRepeatedBankReferenceProduceOneCommand() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { send(AUTH, 1, "in", 51, "9999999999", reference, "FT-1", 200); return null; });
            var second = executor.submit(() -> { send(AUTH, 2, "in", 51, "9999999999", reference, "FT-2", 200); return null; });
            first.get();
            second.get();
        }
        send(AUTH, 3, "in", 51, "9999999999", reference, "FT-1", 200);
        assertThat(outcome(3)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from sepay_credit_events where outcome='PAID'", Integer.class))
                .isEqualTo(1);
        assertThat(commands()).isEqualTo(1);
    }

    @Test
    void oneBankReferenceCannotPayAnotherOrderAndMockReferencesNeverSettle() throws Exception {
        String other = checkout(createOrder());
        assertThat(other).isNotEqualTo(reference);
        send(AUTH, 1, "in", 51, "9999999999", reference, "FT-1", 200);
        send(AUTH, 2, "in", 51, "9999999999", other, "FT-1", 200);
        assertThat(jdbc.queryForObject("select status from payments where reference=?", String.class, other))
                .isEqualTo("PENDING");
        jdbc.update("update payments set reference=? where reference=?", "PAY-LEGACY-FIXTURE", other);
        send(AUTH, 3, "in", 51, "9999999999", "PAY-LEGACY-FIXTURE", "FT-2", 200);
        assertThat(outcome(3)).containsExactly("UNMATCHED_REFERENCE");
        assertThat(jdbc.queryForObject("select status from payments where reference='PAY-LEGACY-FIXTURE'", String.class))
                .isEqualTo("PENDING");
    }

    @Test
    void cancelledAndExpiredOrdersDoNotDispense() throws Exception {
        mvc.perform(patch("/api/v1/orders/{id}/status", orderId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isOk());
        send(AUTH, 1, "in", 51, "9999999999", reference, "FT-1", 200);
        assertThat(outcome(1)).containsExactly("ORDER_NOT_PENDING");

        // Separate expiry case on the same isolated test database.
        jdbc.update("update orders set status='PENDING' where id=?", orderId);
        jdbc.update("update payments set expires_at=? where reference=?", LocalDateTime.now().minusSeconds(1), reference);
        send(AUTH, 2, "in", 51, "9999999999", reference, "FT-2", 200);
        assertThat(outcome(2)).containsExactly("EXPIRED");
        assertThat(commands()).isZero();
    }

    private String checkout(long id) throws Exception {
        String result = mvc.perform(post("/api/v1/orders/{id}/payments/qr", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("SEPAY"))
                .andExpect(jsonPath("$.accountHolder").value("TEST HOLDER"))
                .andExpect(jsonPath("$.qrPayload").value(org.hamcrest.Matchers.containsString("bank=MB&acc=9999999999&amount=51&des=SS")))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("reference").asText();
    }

    private void send(String auth, int id, String direction, int amount, String account, String code,
                      String bankRef, int expectedStatus) throws Exception {
        String time = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"));
        byte[] body = json.writeValueAsBytes(Map.of("id", id, "transferType", direction,
                "transferAmount", amount, "accountNumber", account, "code", code, "content", code,
                "referenceCode", bankRef, "gateway", "MBBank", "transactionDate", time));
        String timestamp = Long.toString(java.time.Instant.now().getEpochSecond());
        var result = mvc.perform(post("/api/v1/webhooks/sepay")
                        .header("X-SePay-Timestamp", timestamp)
                        .header("X-SePay-Signature", sign(auth, timestamp, body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 200) result.andExpect(jsonPath("$.success").value(true));
    }

    private String sign(String secret, String timestamp, byte[] body) throws Exception {
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(body));
    }

    private java.util.List<String> outcome(int id) {
        return jdbc.queryForList("select outcome from sepay_credit_events where transaction_id=?", String.class, id);
    }

    private String paymentStatus() {
        return jdbc.queryForObject("select status from payments where reference=?", String.class, reference);
    }

    private int commands() {
        return jdbc.queryForObject("select count(*) from dispensing_commands where order_id=?", Integer.class, orderId);
    }
}
