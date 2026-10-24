
package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.TransactionSummaryResponse;
import com.scentstation.reporting.enumtype.ReportPeriod;
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

    public TransactionReportController(
            TransactionReportService transactionReportService) {
        this.transactionReportService = transactionReportService;
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
                    period.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid period. Allowed values: day, week, month, custom."
            );
        }

        if (reportPeriod == ReportPeriod.CUSTOM) {
            if (startDate == null || endDate == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "startDate and endDate are required for custom period."
                );
            }

            if (startDate.isAfter(endDate)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "startDate must not be after endDate."
                );
            }
        }

        return transactionReportService.getSummary(
                reportPeriod.name().toLowerCase(Locale.ROOT));
    }
}
