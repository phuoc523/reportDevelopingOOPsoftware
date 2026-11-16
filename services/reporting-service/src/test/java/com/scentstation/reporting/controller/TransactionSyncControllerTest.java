package com.scentstation.reporting.controller;

import com.scentstation.reporting.security.InternalApiKeyVerifier;
import com.scentstation.reporting.service.OrderPaymentSyncService;
import com.scentstation.reporting.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionSyncControllerTest {
    private final TransactionService transactions = mock(TransactionService.class);
    private final OrderPaymentSyncService sync = mock(OrderPaymentSyncService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TransactionSyncController(
            transactions, sync, new InternalApiKeyVerifier("test-internal-key"))).build();

    @Test
    void replayRequiresInternalApiKey() throws Exception {
        mvc.perform(post("/api/reporting/internal/transactions/sync-from-order/PAY-1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reporting/internal/transactions/sync-from-order/PAY-1")
                        .header("X-Internal-Api-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(transactions, sync);
    }

    @Test
    void concurrentDatabaseLockIsReportedAsRetryableConflict() throws Exception {
        when(sync.syncFromOrder("PAY-1")).thenThrow(new CannotAcquireLockException("database details"));
        mvc.perform(post("/api/reporting/internal/transactions/sync-from-order/PAY-1")
                        .header("X-Internal-Api-Key", "test-internal-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("message").value("Concurrent transaction synchronization; retry the request"));
    }

    @Test
    void pushRequiresInternalApiKeyBeforeWriting() throws Exception {
        String body = """
                {"reference":"PAY-1","orderId":"8","amount":25000,"currency":"VND",
                 "status":"PENDING","createdAt":"2026-11-13T10:00:00"}
                """;
        mvc.perform(post("/api/reporting/internal/transactions/sync")
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reporting/internal/transactions/sync")
                        .header("X-Internal-Api-Key", "wrong-key")
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(transactions, sync);
    }
}
