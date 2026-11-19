package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.OperationalReportResponse;
import com.scentstation.reporting.enumtype.ReportPeriod;
import com.scentstation.reporting.security.ReportingPermissions;
import com.scentstation.reporting.service.OperationalAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

@RestController
@RequestMapping("/api/reporting")
public class OperationalReportController {
    private final OperationalAnalyticsService service;
    private final Clock clock;

    public OperationalReportController(OperationalAnalyticsService service,
            @org.springframework.beans.factory.annotation.Value("${reporting.zone-id:Asia/Ho_Chi_Minh}") String zone) {
        this.service = service;
        this.clock = Clock.system(ZoneId.of(zone));
    }

    @GetMapping("/operations")
    public OperationalReportResponse operations(
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) String locationId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) BigDecimal refillThreshold) {
        var actor = ReportingPermissions.current();
        boolean platform = ReportingPermissions.has(actor, ReportingPermissions.PLATFORM);
        if (!platform) {
            if (actor.tenantId() == null || (tenantId != null
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
        try {
            return service.report(tenantId.trim(), range[0], range[1], machineId,
                    locationId, productId, refillThreshold);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
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
