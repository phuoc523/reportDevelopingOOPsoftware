
package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.TransactionSummaryResponse;
import com.scentstation.reporting.enumtype.ReportPeriod;
import com.scentstation.reporting.service.ReportDateRangeValidator;
import com.scentstation.reporting.service.TransactionReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Locale;

@RestController
@RequestMapping("/api/reporting/transactions")
public class TransactionReportController {

    private final TransactionReportService transactionReportService;
    private final ReportDateRangeValidator reportDateRangeValidator;

    public TransactionReportController(
            TransactionReportService transactionReportService,
            ReportDateRangeValidator reportDateRangeValidator) {
        this.transactionReportService = transactionReportService;
        this.reportDateRangeValidator = reportDateRangeValidator;
    }

    @GetMapping("/summary")
    public TransactionSummaryResponse getSummary(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {

        ReportPeriod reportPeriod;

        try {
            reportPeriod = ReportPeriod.valueOf(
                    period.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid period. Allowed values: day, week, month, custom."
            );
        }

        reportDateRangeValidator.validate(
                reportPeriod, startDate, endDate
        );

        return transactionReportService.getSummary(
                reportPeriod,
                startDate,
                endDate
        );
    }
}
