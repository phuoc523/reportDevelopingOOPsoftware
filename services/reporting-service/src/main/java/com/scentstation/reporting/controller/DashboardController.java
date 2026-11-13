package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.DashboardResponse;
import com.scentstation.reporting.enumtype.ReportPeriod;
import com.scentstation.reporting.service.DashboardAggregationService;
import com.scentstation.reporting.security.ReportingPermissions;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@RestController
@RequestMapping("/api/reporting")
public class DashboardController {

    private final DashboardAggregationService dashboardAggregationService;

    public DashboardController(DashboardAggregationService dashboardAggregationService) {
        this.dashboardAggregationService = dashboardAggregationService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "month") String period) {
        ReportPeriod reportPeriod;
        try {
            reportPeriod = ReportPeriod.valueOf(period.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid period. Allowed values: day, week, month, custom.");
        }
        if (reportPeriod == ReportPeriod.CUSTOM) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dashboard does not support custom period. Use day, week, or month.");
        }
        var actor = ReportingPermissions.current();
        boolean platform = ReportingPermissions.has(actor, ReportingPermissions.PLATFORM);
        if (!platform) {
            if (actor.tenantId() == null || !actor.locationIds().isEmpty() || !actor.machineIds().isEmpty()
                    || (tenantId != null && !actor.tenantId().toString().equals(tenantId.trim()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant or assigned scope is not supported by this report");
            }
            tenantId = actor.tenantId().toString();
        }
        var dashboard = dashboardAggregationService.getDashboard(tenantId, reportPeriod, platform);
        return dashboard.withAccess(new DashboardResponse.ReportAccess(platform ? "platform" : "tenant",
                tenantId == null ? null : tenantId.trim(), platform,
                platform && ReportingPermissions.has(actor, ReportingPermissions.EXPORT)));
    }
}
