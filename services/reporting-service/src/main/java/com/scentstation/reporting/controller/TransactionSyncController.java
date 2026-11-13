package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.TransactionResponse;
import com.scentstation.reporting.dto.TransactionSyncRequest;
import com.scentstation.reporting.security.InternalApiKeyVerifier;
import com.scentstation.reporting.service.OrderPaymentSyncService;
import com.scentstation.reporting.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/reporting/internal/transactions")
public class TransactionSyncController {

    @ExceptionHandler(PessimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public java.util.Map<String, String> concurrentSync() {
        return java.util.Map.of("message", "Concurrent transaction synchronization; retry the request");
    }

    private final TransactionService transactionService;
    private final OrderPaymentSyncService orderPaymentSyncService;
    private final InternalApiKeyVerifier apiKeyVerifier;

    public TransactionSyncController(
            TransactionService transactionService,
            OrderPaymentSyncService orderPaymentSyncService,
            InternalApiKeyVerifier apiKeyVerifier) {
        this.transactionService = transactionService;
        this.orderPaymentSyncService = orderPaymentSyncService;
        this.apiKeyVerifier = apiKeyVerifier;
    }

    @PostMapping("/sync")
    public ResponseEntity<TransactionResponse> syncTransaction(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String providedKey,
            @Valid @RequestBody TransactionSyncRequest request) {
        apiKeyVerifier.verify(providedKey);
        return ResponseEntity.ok(transactionService.syncTransaction(request));
    }

    @PostMapping("/sync-from-order/{reference}")
    public ResponseEntity<TransactionResponse> syncFromOrder(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String providedKey,
            @PathVariable String reference) {
        apiKeyVerifier.verify(providedKey);
        return ResponseEntity.ok(orderPaymentSyncService.syncFromOrder(reference));
    }
}
