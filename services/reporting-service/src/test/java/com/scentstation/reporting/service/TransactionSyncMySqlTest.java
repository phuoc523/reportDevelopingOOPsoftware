package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionSyncRequest;
import com.scentstation.reporting.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: real MySQL, separate database; never clears existing data. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "REPORTING_MYSQL_TEST_URL", matches = ".+")
class TransactionSyncMySqlTest {
    @Autowired TransactionService transactions;
    @Autowired TransactionRepository repository;
    @Autowired OrderPaymentSyncService orderSync;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("REPORTING_MYSQL_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("REPORTING_MYSQL_TEST_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("REPORTING_MYSQL_TEST_PASSWORD"));
    }

    private TransactionSyncRequest request(String reference, String status) {
        var created = LocalDateTime.of(2026, 11, 13, 10, 0);
        return new TransactionSyncRequest(reference, "phase2-test", new BigDecimal("25.50"), "VND",
                status, created, "PAID".equals(status) ? created.plusMinutes(1) : null);
    }

    private int attempt(TransactionSyncRequest request, CountDownLatch start) throws Exception {
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            transactions.syncTransaction(request);
            return 200;
        } catch (ResponseStatusException ex) {
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
            return 409;
        } catch (PessimisticLockingFailureException ex) {
            return 409;
        }
    }

    private List<Integer> race(TransactionSyncRequest first, TransactionSyncRequest second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit((Callable<Integer>) () -> attempt(first, start));
            var b = pool.submit((Callable<Integer>) () -> attempt(second, start));
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void simultaneousFirstDeliveriesLeaveOneRowAndCanBeReplayed() throws Exception {
        String reference = "PHASE2-" + UUID.randomUUID();
        var pending = request(reference, "PENDING");
        assertTrue(race(pending, pending).contains(200));
        var replay = transactions.syncTransaction(pending);
        assertEquals(replay.id(), transactions.syncTransaction(pending).id());
        assertEquals(1, jdbc.queryForObject("select count(*) from transactions where reference = ?",
                Integer.class, reference));
    }

    @Test
    void concurrentPendingAndPaidCannotLosePaidState() throws Exception {
        String reference = "PHASE2-" + UUID.randomUUID();
        transactions.syncTransaction(request(reference, "PENDING"));
        race(request(reference, "PAID"), request(reference, "PENDING"));
        var stored = repository.findByReference(reference).orElseThrow();
        assertEquals("PAID", stored.getStatus());
        assertNotNull(stored.getPaidAt());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ORDER_SERVICE_URL", matches = ".+")
    void readsRealOrderPaymentAndReplaysPendingToPaid() {
        RestClient order = RestClient.create(System.getenv("ORDER_SERVICE_URL"));
        Map<?, ?> created = order.post().uri("/api/v1/orders")
                .body(Map.of("tenantId", "1", "customerId", 7, "machineId", "PHASE2-TEST", "slotId", "SLOT-01",
                        "items", List.of(Map.of("productId", 3, "productName", "Phase 2 integration test",
                                "unitPrice", 25000, "quantity", 1))))
                .retrieve().body(Map.class);
        assertNotNull(created);
        Map<?, ?> payment = order.post().uri("/api/v1/orders/{id}/payments/qr", created.get("id"))
                .retrieve().body(Map.class);
        assertNotNull(payment);
        String reference = (String) payment.get("reference");
        var pending = orderSync.syncFromOrder(reference);
        assertEquals("PENDING", pending.status());
        order.post().uri("/api/v1/payments/{reference}/simulate", reference).retrieve().toBodilessEntity();
        var paid = orderSync.syncFromOrder(reference);
        assertEquals("PAID", paid.status());
        assertEquals(pending.id(), paid.id());
        assertEquals(0, new BigDecimal("25000").compareTo(paid.amount()));
        assertEquals(paid, orderSync.syncFromOrder(reference));
    }
}
