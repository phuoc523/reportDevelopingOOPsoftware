package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionSummaryResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TransactionReportService {

    public TransactionSummaryResponse getSummary(String period) {
        return new TransactionSummaryResponse(
                100,
                75,
                5,
                8,
                7,
                5,
                new BigDecimal("1500000.00"),
                period
        );
    }
}
