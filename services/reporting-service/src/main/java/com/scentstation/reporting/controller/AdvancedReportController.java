package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.*;
import com.scentstation.reporting.service.AdvancedReportService;
import com.scentstation.reporting.service.TransactionQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reporting")
public class AdvancedReportController {
    private final AdvancedReportService reportService;
    private final TransactionQueryService queryService;

    public AdvancedReportController(AdvancedReportService reportService,
                                    TransactionQueryService queryService) {
        this.reportService = reportService;
        this.queryService = queryService;
    }

    @GetMapping("/revenue/details")
    public java.util.List<RevenueDetailResponse> revenueDetails(
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return reportService.revenueDetails(startDate, endDate);
    }

    @GetMapping("/transactions/status-summary")
    public TransactionStatusSummaryResponse statusSummary(
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return reportService.statusSummary(startDate, endDate);
    }

    @GetMapping("/refunds/summary")
    public RefundSummaryResponse refundSummary(
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return reportService.refundSummary(startDate, endDate);
    }

    @GetMapping("/refunds")
    public Page<TransactionResponse> refunds(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "page must be >= 0 and size must be >= 1");
        }
        return queryService.search(new TransactionFilterRequest(null, null, null,
                        startDate, endDate, null, null),
                PageRequest.of(page, Math.min(size, 200),
                        Sort.by(Sort.Direction.DESC, "createdAt")), true);
    }
}

