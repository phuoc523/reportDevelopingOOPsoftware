package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.TransactionSummaryResponse;
import com.scentstation.reporting.service.TransactionReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reporting/transactions")
public class TransactionReportController {

    private final TransactionReportService transactionReportService;

    public TransactionReportController(
            TransactionReportService transactionReportService) {
        this.transactionReportService = transactionReportService;
    }

    @GetMapping("/summary")
    public TransactionSummaryResponse getSummary(
            @RequestParam(defaultValue = "month") String period) {
        return transactionReportService.getSummary(period);
    }
}
