package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.OperationalReportResponse;
import com.scentstation.reporting.dto.TransactionFilterRequest;
import com.scentstation.reporting.enumtype.ReportPeriod;
import com.scentstation.reporting.security.ReportingPermissions;
import com.scentstation.reporting.security.ReportingTenantScope;
import com.scentstation.reporting.service.ExcelExportService;
import com.scentstation.reporting.service.OperationalAnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

@RestController
@RequestMapping("/api/reporting/export")
public class ExcelExportController {
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=utf-8");
    private final ExcelExportService exportService;
    private OperationalAnalyticsService operationalAnalyticsService;
    private final Clock clock;

    @Autowired
    public ExcelExportController(ExcelExportService exportService,
            @Value("${reporting.zone-id:Asia/Ho_Chi_Minh}") String zone) {
        this.exportService = exportService;
        this.clock = Clock.system(ZoneId.of(zone));
    }

    public ExcelExportController(ExcelExportService exportService) {
        this(exportService, "Asia/Ho_Chi_Minh");
    }

    @Autowired(required = false)
    public void setOperationalAnalyticsService(OperationalAnalyticsService operationalAnalyticsService) {
        this.operationalAnalyticsService = operationalAnalyticsService;
    }

    @GetMapping("/transactions")
    public ResponseEntity<byte[]> exportTransactions(
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) String slotId,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "xlsx") String format) {
        boolean csv = isCsv(format);
        TransactionFilterRequest filter = new TransactionFilterRequest(
                reference, orderId, status, startDate, endDate, minAmount, maxAmount,
                ReportingTenantScope.resolve(tenantId), machineId, slotId, productId);
        byte[] file = csv ? exportService.exportTransactionsCsv(filter) : exportService.exportTransactions(filter);
        return ResponseEntity.ok().contentType(csv ? CSV : XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + (csv ? "transactions.csv" : "transactions.xlsx"))
                .body(file);
    }

    @GetMapping("/revenue")
    public ResponseEntity<byte[]> exportRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "xlsx") String format) {
        boolean csv = isCsv(format);
        String resolvedTenant = ReportingTenantScope.resolve(tenantId);
        byte[] file = csv
                ? exportService.exportRevenueCsv(startDate, endDate, resolvedTenant, machineId, productId)
                : exportService.exportRevenue(startDate, endDate, resolvedTenant, machineId, productId);
        return ResponseEntity.ok().contentType(csv ? CSV : XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + (csv ? "revenue-report.csv" : "revenue-report.xlsx"))
                .body(file);
    }

    @GetMapping("/operations")
    public ResponseEntity<byte[]> exportOperations(
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) String locationId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) BigDecimal refillThreshold,
            @RequestParam(defaultValue = "xlsx") String format) {
        if (operationalAnalyticsService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Operational export is unavailable");
        }
        boolean csv = isCsv(format);
        var actor = ReportingPermissions.current();
        boolean platform = ReportingPermissions.has(actor, ReportingPermissions.PLATFORM);
        if (!platform) {
            if (actor.tenantId() == null || (tenantId != null && !tenantId.isBlank()
                    && !actor.tenantId().toString().equals(tenantId.trim()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant access is not allowed");
            }
            tenantId = actor.tenantId().toString();
            if (machineId != null && !actor.machineIds().isEmpty() && !actor.machineIds().contains(machineId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Machine access is not allowed");
            }
            if (locationId != null && !locationId.isBlank() && !actor.locationIds().isEmpty()) {
                long requested;
                try { requested = Long.parseLong(locationId.trim()); }
                catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Location access is not allowed"); }
                if (!actor.locationIds().contains(requested)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Location access is not allowed");
                }
            }
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required");
        }
        LocalDate[] range = range(period, startDate, endDate);
        OperationalReportResponse report;
        try {
            report = operationalAnalyticsService.report(tenantId.trim(), range[0], range[1], machineId,
                    locationId, productId, refillThreshold,
                    platform ? java.util.Set.<Long>of() : actor.machineIds(),
                    platform ? java.util.Set.<Long>of() : actor.locationIds());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        byte[] file = csv ? exportService.exportOperationsCsv(report) : exportService.exportOperationsExcel(report);
        return ResponseEntity.ok().contentType(csv ? CSV : XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + (csv ? "operations-report.csv" : "operations-report.xlsx"))
                .body(file);
    }

    private boolean isCsv(String format) {
        String normalized = format == null ? "xlsx" : format.trim().toLowerCase(Locale.ROOT);
        if ("xlsx".equals(normalized)) return false;
        if ("csv".equals(normalized)) return true;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported export format. Allowed values: xlsx, csv.");
    }

    private LocalDate[] range(String value, LocalDate start, LocalDate end) {
        ReportPeriod period;
        try { period = ReportPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid period. Allowed values: day, week, month, custom.");
        }
        LocalDate today = LocalDate.now(clock);
        return switch (period) {
            case DAY -> new LocalDate[]{today, today};
            case WEEK -> {
                LocalDate from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new LocalDate[]{from, from.plusDays(6)};
            }
            case MONTH -> new LocalDate[]{today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth())};
            case CUSTOM -> {
                if (start == null || end == null || start.isAfter(end)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "custom period requires startDate <= endDate");
                }
                yield new LocalDate[]{start, end};
            }
        };
    }
}

