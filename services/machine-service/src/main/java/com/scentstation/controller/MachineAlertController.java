package com.scentstation.controller;

import com.scentstation.dto.request.AlertActionRequest;
import com.scentstation.dto.request.LowStockAlertRequest;
import com.scentstation.dto.response.MachineAlertResponse;
import com.scentstation.entity.MachineAlertSeverity;
import com.scentstation.entity.MachineAlertStatus;
import com.scentstation.service.InternalServiceKeyVerifier;
import com.scentstation.service.MachineAlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class MachineAlertController {
    private final MachineAlertService alerts;
    private final InternalServiceKeyVerifier serviceKeyVerifier;

    public MachineAlertController(MachineAlertService alerts,
                                  InternalServiceKeyVerifier serviceKeyVerifier) {
        this.alerts = alerts;
        this.serviceKeyVerifier = serviceKeyVerifier;
    }

    @GetMapping
    public List<MachineAlertResponse> list(
            @RequestParam String tenantId,
            @RequestParam(required = false) MachineAlertStatus status,
            @RequestParam(required = false) MachineAlertSeverity severity) {
        return alerts.list(tenantId, status, severity).stream()
            .map(MachineAlertResponse::new).toList();
    }

    @GetMapping("/{alertId}")
    public MachineAlertResponse get(@PathVariable Long alertId, @RequestParam String tenantId) {
        return new MachineAlertResponse(alerts.get(alertId, tenantId));
    }

    @PostMapping("/{alertId}/acknowledge")
    public MachineAlertResponse acknowledge(@PathVariable Long alertId,
            @RequestParam String tenantId, @Valid @RequestBody AlertActionRequest request) {
        return new MachineAlertResponse(alerts.acknowledge(alertId, tenantId, request.getActorId()));
    }

    @PostMapping("/{alertId}/assign")
    public MachineAlertResponse assign(@PathVariable Long alertId,
            @RequestParam String tenantId, @Valid @RequestBody AlertActionRequest request) {
        return new MachineAlertResponse(alerts.assign(alertId, tenantId, request.getAssigneeId()));
    }

    @PostMapping("/{alertId}/resolve")
    public MachineAlertResponse resolve(@PathVariable Long alertId,
            @RequestParam String tenantId, @Valid @RequestBody AlertActionRequest request) {
        return new MachineAlertResponse(alerts.resolve(alertId, tenantId,
            request.getActorId(), request.getNotes()));
    }

    @PostMapping("/{alertId}/reopen")
    public MachineAlertResponse reopen(@PathVariable Long alertId,
            @RequestParam String tenantId, @Valid @RequestBody AlertActionRequest request) {
        return new MachineAlertResponse(alerts.reopen(alertId, tenantId, request.getNotes()));
    }

    @PostMapping("/integration/low-stock")
    @ResponseStatus(HttpStatus.CREATED)
    public MachineAlertResponse lowStock(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String serviceKey,
            @Valid @RequestBody LowStockAlertRequest request) {
        serviceKeyVerifier.verify(serviceKey);
        return new MachineAlertResponse(alerts.recordLowStock(request));
    }
}
