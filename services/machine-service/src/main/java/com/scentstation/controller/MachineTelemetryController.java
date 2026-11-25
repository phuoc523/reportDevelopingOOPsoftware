package com.scentstation.controller;

import com.scentstation.dto.response.MachineDeviceEventResponse;
import com.scentstation.dto.response.MachineTelemetryResponse;
import com.scentstation.repository.MachineDeviceEventRepository;
import com.scentstation.repository.MachineTelemetryLatestRepository;
import com.scentstation.security.MachineAuthorization;
import com.scentstation.security.MachineCurrentActorContext;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/machines")
public class MachineTelemetryController {
    private final MachineTelemetryLatestRepository telemetry;
    private final MachineDeviceEventRepository deviceEvents;
    private final MachineCurrentActorContext actors;
    private final MachineAuthorization authorization;

    public MachineTelemetryController(MachineTelemetryLatestRepository telemetry,
                                      MachineDeviceEventRepository deviceEvents,
                                      MachineCurrentActorContext actors,
                                      MachineAuthorization authorization) {
        this.telemetry = telemetry;
        this.deviceEvents = deviceEvents;
        this.actors = actors;
        this.authorization = authorization;
    }

    @GetMapping("/{machineId}/telemetry")
    public List<MachineTelemetryResponse> latest(@PathVariable @NonNull Long machineId) {
        authorization.requireMachine(actors.principal(), machineId, MachineAuthorization.MACHINE_READ);
        return telemetry.findAllByMachineIdOrderBySlotIdAsc(machineId).stream()
            .map(MachineTelemetryResponse::from)
            .toList();
    }

    @GetMapping("/{machineId}/events")
    public List<MachineDeviceEventResponse> events(@PathVariable @NonNull Long machineId) {
        authorization.requireMachine(actors.principal(), machineId, MachineAuthorization.MACHINE_READ);
        return deviceEvents.findAllByMachineIdOrderByOccurredAtDesc(machineId).stream()
            .map(MachineDeviceEventResponse::from)
            .toList();
    }
}
