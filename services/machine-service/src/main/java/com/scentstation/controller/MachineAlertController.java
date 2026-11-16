package com.scentstation.controller;

import com.scentstation.dto.request.AlertActionRequest;
import com.scentstation.dto.request.LowStockAlertRequest;
import com.scentstation.dto.response.MachineAlertResponse;
import com.scentstation.entity.MachineAlertSeverity;
import com.scentstation.entity.MachineAlertStatus;
import com.scentstation.service.InternalServiceKeyVerifier;
import com.scentstation.service.MachineAlertService;
import com.scentstation.security.MachineAuthorization;
import com.scentstation.security.MachineCurrentActorContext;
import com.scentstation.entity.MachineAlert;
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
    private final MachineCurrentActorContext actors;
    private final MachineAuthorization authorization;

    public MachineAlertController(MachineAlertService alerts,
                                  InternalServiceKeyVerifier serviceKeyVerifier,
                                  MachineCurrentActorContext actors,
                                  MachineAuthorization authorization) {
        this.alerts = alerts;
        this.serviceKeyVerifier = serviceKeyVerifier;
        this.actors = actors;
        this.authorization = authorization;
    }

    @GetMapping
    public List<MachineAlertResponse> list(
            @RequestParam String tenantId,
            @RequestParam(required = false) MachineAlertStatus status,
            @RequestParam(required = false) MachineAlertSeverity severity) {
        var actor = authorization.require(actors.principal(), MachineAuthorization.ALERT_READ);
        authorization.requireTenant(actor, tenantId);
        return alerts.list(tenantId, status, severity).stream()
            .filter(alert -> {
                try {
                    authorization.requireAlert(actor, alert, MachineAuthorization.ALERT_READ);
                    return true;
                } catch (org.springframework.web.server.ResponseStatusException exception) {
                    return false;
                }
            })
            .map(MachineAlertResponse::new).toList();
    }

    @GetMapping("/{alertId}")
    public MachineAlertResponse get(@PathVariable Long alertId, @RequestParam String tenantId) {
        var actor = authorization.require(actors.principal(), MachineAuthorization.ALERT_READ);
        authorization.requireTenant(actor, tenantId);
        MachineAlert alert = alerts.get(alertId, tenantId);
        authorization.requireAlert(actor, alert, MachineAuthorization.ALERT_READ);
        return new MachineAlertResponse(alert);
    }

    @PostMapping("/{alertId}/acknowledge")
    public MachineAlertResponse acknowledge(@PathVariable Long alertId,
            @RequestParam String tenantId, @Valid @RequestBody AlertActionRequest request) {
        var actor = actors.principal();
        authorization.require(actor, MachineAuthorization.ALERT_MANAGE);
        authorization.requireTenant(actor, tenantId);
        authorization.requireAlert(actor, alerts.get(alertId, tenantId), MachineAuthorization.ALERT_MANAGE);
        return new MachineAlertResponse(alerts.acknowledge(alertId, tenantId, actor.id().toString()));
    }

    @PostMapping("/{alertId}/assign")
    public MachineAlertResponse assign(@PathVariable Long alertId,
            @RequestParam String tenantId, @Valid @RequestBody AlertActionRequest request) {
        var actor = actors.principal();
        authorization.require(actor, MachineAuthorization.ALERT_MANAGE);
        authorization.requireTenant(actor, tenantId);
        authorization.requireAlert(actor, alerts.get(alertId, tenantId), MachineAuthorization.ALERT_MANAGE);
        return new MachineAlertResponse(alerts.assign(alertId, tenantId, request.getAssigneeId()));
    }

    @PostMapping("/{alertId}/resolve")
    public MachineAlertResponse resolve(@PathVariable Long alertId,
            @RequestParam String tenantId, @Valid @RequestBody AlertActionRequest request) {
        var actor = actors.principal();
        authorization.require(actor, MachineAuthorization.ALERT_MANAGE);
        authorization.requireTenant(actor, tenantId);
        authorization.requireAlert(actor, alerts.get(alertId, tenantId), MachineAuthorization.ALERT_MANAGE);
        return new MachineAlertResponse(alerts.resolve(alertId, tenantId,
            actor.id().toString(), request.getNotes()));
    }

    @PostMapping("/{alertId}/reopen")
    public MachineAlertResponse reopen(@PathVariable Long alertId,
            @RequestParam String tenantId, @Valid @RequestBody AlertActionRequest request) {
        var actor = actors.principal();
        authorization.require(actor, MachineAuthorization.ALERT_MANAGE);
        authorization.requireTenant(actor, tenantId);
        authorization.requireAlert(actor, alerts.get(alertId, tenantId), MachineAuthorization.ALERT_MANAGE);
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
