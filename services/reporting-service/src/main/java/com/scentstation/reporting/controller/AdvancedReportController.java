package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.*;
import com.scentstation.reporting.service.AdvancedReportService;
import com.scentstation.reporting.service.TransactionQueryService;
import com.scentstation.reporting.security.ReportingTenantScope;
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
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) Long productId) {
        return reportService.revenueDetails(startDate, endDate,
                ReportingTenantScope.resolve(tenantId), machineId, productId);
    }

    @GetMapping("/revenue/breakdown")
    public RevenueBreakdownResponse revenueBreakdown(
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) Long productId) {
        return reportService.revenueBreakdown(startDate, endDate,
                ReportingTenantScope.resolve(tenantId), machineId, productId);
    }

    @GetMapping("/transactions/status-summary")
    public TransactionStatusSummaryResponse statusSummary(
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) Long productId) {
        return reportService.statusSummary(startDate, endDate,
                ReportingTenantScope.resolve(tenantId), machineId, productId);
    }

    @GetMapping("/refunds/summary")
    public RefundSummaryResponse refundSummary(
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) Long productId) {
        return reportService.refundSummary(startDate, endDate,
                ReportingTenantScope.resolve(tenantId), machineId, productId);
    }

    @GetMapping("/refunds")
    public Page<TransactionResponse> refunds(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "page must be >= 0 and size must be >= 1");
        }
        return queryService.search(new TransactionFilterRequest(null, null, null,
                        startDate, endDate, null, null, ReportingTenantScope.resolve(tenantId),
                        machineId, null, productId),
                PageRequest.of(page, Math.min(size, 200),
                        Sort.by(Sort.Direction.DESC, "createdAt")), true);
    }
}

