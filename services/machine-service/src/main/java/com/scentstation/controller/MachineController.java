package com.scentstation.controller;

import com.scentstation.dto.request.MachineRequests.*;
import com.scentstation.dto.request.MaintenanceCompletionRequest;
import com.scentstation.dto.response.MachineResponses.*;
import com.scentstation.service.AvailabilityService;
import com.scentstation.service.MachineService;
import com.scentstation.security.MachineAuthorization;
import com.scentstation.security.MachineCurrentActorContext;
import com.scentstation.integration.identity.dto.IdentityPrincipal;
import com.scentstation.entity.DeviceRegistrationStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/machines")
public class MachineController {
    private final MachineService machineService;
    private final AvailabilityService availabilityService;
    private final MachineCurrentActorContext actors;
    private final MachineAuthorization authorization;

    public MachineController(MachineService machineService, AvailabilityService availabilityService,
                             MachineCurrentActorContext actors, MachineAuthorization authorization) {
        this.machineService = machineService;
        this.availabilityService = availabilityService;
        this.actors = actors;
        this.authorization = authorization;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MachineResponse register(@Valid @RequestBody CreateMachineRequest request) {
        IdentityPrincipal actor = authorization.require(actors.principal(), MachineAuthorization.MACHINE_CONFIGURE);
        authorization.requireMachineCreation(actor);
        authorization.requireTenant(actor, request.getTenantId());
        authorization.requireLocation(actor, request.getLocationId());
        return machineService.register(request);
    }

    @GetMapping
    public List<MachineResponse> list(@RequestParam(required = false) String tenantId) {
        IdentityPrincipal actor = actors.principal();
        if (tenantId != null) authorization.requireTenant(actor, tenantId);
        return authorization.listMachines(actor, MachineAuthorization.MACHINE_READ).stream()
            .map(MachineResponse::from).toList();
    }

    @GetMapping("/{machineId}")
    public MachineResponse get(@PathVariable @NonNull Long machineId) {
        authorization.requireMachine(actors.principal(), machineId, MachineAuthorization.MACHINE_READ);
        return machineService.get(machineId);
    }

    @PutMapping("/{machineId}")
    public MachineResponse update(@PathVariable @NonNull Long machineId,
                                  @Valid @RequestBody UpdateMachineRequest request) {
        IdentityPrincipal actor = actors.principal();
        authorization.requireMachine(actor, machineId, MachineAuthorization.MACHINE_CONFIGURE);
        authorization.requireTenant(actor, request.getTenantId());
        authorization.requireLocation(actor, request.getLocationId());
        return machineService.update(machineId, request, actor.id().toString());
    }

    @PatchMapping("/{machineId}/device-registration-status")
    public MachineResponse updateDeviceRegistrationStatus(
            @PathVariable @NonNull Long machineId,
            @Valid @RequestBody UpdateDeviceRegistrationStatusRequest request) {
        IdentityPrincipal actor = actors.principal();
        String permission = request.getStatus() == DeviceRegistrationStatus.REVOKED
            ? MachineAuthorization.DEVICE_ACCESS_REVOKE : MachineAuthorization.MACHINE_CONFIGURE;
        if (request.getStatus() == DeviceRegistrationStatus.REVOKED) {
            authorization.requireMachineForRevocation(actor, machineId);
        } else {
            authorization.requireMachine(actor, machineId, permission);
        }
        return machineService.updateDeviceRegistrationStatus(machineId, request, actor.id().toString());
    }

    @PatchMapping("/{machineId}/operational-status")
    public MachineResponse updateOperationalStatus(
            @PathVariable @NonNull Long machineId,
            @Valid @RequestBody UpdateOperationalStatusRequest request) {
        IdentityPrincipal actor = actors.principal();
        authorization.requireMachine(actor, machineId, MachineAuthorization.MACHINE_CONTROL);
        return machineService.updateOperationalStatus(machineId, request, actor.id().toString());
    }

    @PatchMapping("/{machineId}/control")
    public MachineResponse control(@PathVariable @NonNull Long machineId,
                                   @Valid @RequestBody MachineControlRequest request) {
        IdentityPrincipal actor = actors.principal();
        authorization.requireMachine(actor, machineId, MachineAuthorization.MACHINE_CONTROL);
        return machineService.control(machineId, request, actor.id().toString());
    }

    @PostMapping("/{machineId}/maintenance-completion")
    public MachineResponse completeMaintenance(@PathVariable @NonNull Long machineId,
                                               @Valid @RequestBody MaintenanceCompletionRequest request) {
        IdentityPrincipal actor = actors.principal();
        authorization.requireMachine(actor, machineId, MachineAuthorization.MAINTENANCE_MANAGE);
        return machineService.completeMaintenance(machineId, new MaintenanceCompletionRequest(
            request.checklistItems(), request.postMaintenanceTestPassed(), actor.id().toString()));
    }

    @PutMapping("/{machineId}/operating-hours")
    public MachineResponse updateOperatingHours(
            @PathVariable @NonNull Long machineId,
            @Valid @RequestBody UpdateOperatingHoursRequest request) {
        IdentityPrincipal actor = actors.principal();
        authorization.requireMachine(actor, machineId, MachineAuthorization.MACHINE_CONFIGURE);
        return machineService.updateOperatingHours(machineId, request, actor.id().toString());
    }

    @GetMapping("/{machineId}/availability")
    public MachineAvailabilityResponse availability(@PathVariable @NonNull Long machineId) {
        authorization.requireMachine(actors.principal(), machineId, MachineAuthorization.MACHINE_READ);
        return availabilityService.getAvailability(machineId);
    }

    @GetMapping("/{machineId}/history")
    public List<MachineHistoryResponse> history(@PathVariable @NonNull Long machineId) {
        authorization.requireMachine(actors.principal(), machineId, MachineAuthorization.MAINTENANCE_READ);
        return machineService.history(machineId);
    }
}
