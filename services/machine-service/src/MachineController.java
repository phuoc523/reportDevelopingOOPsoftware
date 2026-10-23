package com.scentstation;

import com.scentstation.MachineRequests.*;
import com.scentstation.MachineResponses.*;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/machines")
public class MachineController {
    private final MachineService machineService;
    private final AvailabilityService availabilityService;

    public MachineController(MachineService machineService, AvailabilityService availabilityService) {
        this.machineService = machineService;
        this.availabilityService = availabilityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MachineResponse register(@Valid @RequestBody CreateMachineRequest request) {
        return machineService.register(request);
    }

    @GetMapping
    public List<MachineResponse> list(@RequestParam(required = false) String tenantId) {
        return machineService.list(tenantId);
    }

    @GetMapping("/{machineId}")
    public MachineResponse get(@PathVariable Long machineId) {
        return machineService.get(machineId);
    }

    @PutMapping("/{machineId}")
    public MachineResponse update(@PathVariable Long machineId,
                                  @Valid @RequestBody UpdateMachineRequest request,
                                  @RequestHeader(value = "X-Actor-Id", required = false) String actorId) {
        return machineService.update(machineId, request, actorId);
    }

    @PatchMapping("/{machineId}/device-registration-status")
    public MachineResponse updateDeviceRegistrationStatus(
            @PathVariable Long machineId,
            @Valid @RequestBody UpdateDeviceRegistrationStatusRequest request,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId) {
        return machineService.updateDeviceRegistrationStatus(machineId, request, actorId);
    }

    @PatchMapping("/{machineId}/operational-status")
    public MachineResponse updateOperationalStatus(
            @PathVariable Long machineId,
            @Valid @RequestBody UpdateOperationalStatusRequest request,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId) {
        return machineService.updateOperationalStatus(machineId, request, actorId);
    }

    @PatchMapping("/{machineId}/control")
    public MachineResponse control(@PathVariable Long machineId,
                                   @Valid @RequestBody MachineControlRequest request,
                                   @RequestHeader(value = "X-Actor-Id", required = false) String actorId) {
        return machineService.control(machineId, request, actorId);
    }

    @PutMapping("/{machineId}/operating-hours")
    public MachineResponse updateOperatingHours(
            @PathVariable Long machineId,
            @Valid @RequestBody UpdateOperatingHoursRequest request,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId) {
        return machineService.updateOperatingHours(machineId, request, actorId);
    }

    @PostMapping("/{machineId}/heartbeat")
    public MachineResponse heartbeat(@PathVariable Long machineId) {
        return machineService.heartbeat(machineId);
    }

    @GetMapping("/{machineId}/availability")
    public MachineAvailabilityResponse availability(@PathVariable Long machineId) {
        return availabilityService.getAvailability(machineId);
    }

    @GetMapping("/{machineId}/history")
    public List<MachineHistoryResponse> history(@PathVariable Long machineId) {
        return machineService.history(machineId);
    }
}
