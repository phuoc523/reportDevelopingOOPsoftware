package com.scentstation.controller;

import com.scentstation.dto.response.MachineDeviceEventResponse;
import com.scentstation.dto.response.HistoryPageResponse;
import com.scentstation.dto.response.MachineTelemetryResponse;
import com.scentstation.service.MachineIoTService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.NonNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/machines/{machineId}")
@Validated
public class MachineIoTController {
    private final MachineIoTService machineIoTService;

    public MachineIoTController(MachineIoTService machineIoTService) {
        this.machineIoTService = machineIoTService;
    }

    @GetMapping("/telemetry")
    public HistoryPageResponse<MachineTelemetryResponse> telemetry(@PathVariable @NonNull Long machineId,
            @RequestParam String tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return machineIoTService.telemetry(machineId, tenantId, from, until, page, size);
    }

    @GetMapping("/device-events")
    public HistoryPageResponse<MachineDeviceEventResponse> events(@PathVariable @NonNull Long machineId,
            @RequestParam String tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return machineIoTService.events(machineId, tenantId, from, until, page, size);
    }
}
