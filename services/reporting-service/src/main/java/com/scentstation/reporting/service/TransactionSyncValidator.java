package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionSyncRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** Shared validation for HTTP pushes and payments read directly from Order. */
final class TransactionSyncValidator {
    private static final Set<String> STATUSES = Set.of("PENDING", "PAID", "FAILED", "EXPIRED", "REFUNDED");

    private TransactionSyncValidator() {}

    static void validate(TransactionSyncRequest request, HttpStatus errorStatus) {
        if (request == null
                || invalidText(request.reference(), 100)
                || invalidText(request.orderId(), 100)
                || invalidText(request.currency(), 10)
                || invalidText(request.status(), 30)
                || request.createdAt() == null
                || request.amount() == null
                || request.amount().compareTo(new BigDecimal("0.01")) < 0
                || request.amount().compareTo(new BigDecimal("9999999999999.99")) > 0
                || request.amount().stripTrailingZeros().scale() > 2) {
            throw new ResponseStatusException(errorStatus, "Invalid transaction data");
        }
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)
                || ("PAID".equals(status) && request.paidAt() == null)
                || (!"PAID".equals(status) && request.paidAt() != null)
                || (request.paidAt() != null && request.paidAt().isBefore(request.createdAt()))) {
            throw new ResponseStatusException(errorStatus, "Invalid transaction status or timestamps");
        }
    }

    private static boolean invalidText(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }
}
