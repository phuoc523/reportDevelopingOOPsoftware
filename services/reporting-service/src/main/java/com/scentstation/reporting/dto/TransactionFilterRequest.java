package com.scentstation.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionFilterRequest(
        String reference,
        String orderId,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {}

