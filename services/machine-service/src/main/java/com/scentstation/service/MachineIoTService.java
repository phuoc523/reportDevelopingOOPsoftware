package com.scentstation.service;

import com.scentstation.dto.response.MachineDeviceEventResponse;
import com.scentstation.dto.response.HistoryPageResponse;
import com.scentstation.dto.response.MachineTelemetryResponse;
import com.scentstation.entity.Machine;
import com.scentstation.exception.InvalidOperationException;
import com.scentstation.exception.ResourceNotFoundException;
import com.scentstation.repository.MachineDeviceEventRepository;
import com.scentstation.repository.MachineRepository;
import com.scentstation.repository.MachineTelemetryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;

import java.time.Instant;

@Service
public class MachineIoTService {
    private final MachineRepository machines;
    private final MachineTelemetryRepository telemetry;
    private final MachineDeviceEventRepository deviceEvents;

    public MachineIoTService(MachineRepository machines, MachineTelemetryRepository telemetry,
                             MachineDeviceEventRepository deviceEvents) {
        this.machines = machines;
        this.telemetry = telemetry;
        this.deviceEvents = deviceEvents;
    }

    @Transactional(readOnly = true)
    public HistoryPageResponse<MachineTelemetryResponse> telemetry(@NonNull Long machineId, String tenantId,
            Instant from, Instant until, int page, int size) {
        requireTenantMachine(machineId, tenantId);
        validateRange(from, until, page, size);
        Page<MachineTelemetryResponse> history = telemetry.findHistory(machineId, from, until,
                pageRequest(page, size))
            .map(MachineTelemetryResponse::new);
        return HistoryPageResponse.from(history);
    }

    @Transactional(readOnly = true)
    public HistoryPageResponse<MachineDeviceEventResponse> events(@NonNull Long machineId, String tenantId,
            Instant from, Instant until, int page, int size) {
        requireTenantMachine(machineId, tenantId);
        validateRange(from, until, page, size);
        Page<MachineDeviceEventResponse> history = deviceEvents.findHistory(machineId, from, until,
                pageRequest(page, size))
            .map(MachineDeviceEventResponse::new);
        return HistoryPageResponse.from(history);
    }

    private void requireTenantMachine(@NonNull Long machineId, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new InvalidOperationException("tenantId is required to access device history");
        }
        Machine machine = machines.findById(machineId)
            .orElseThrow(() -> new ResourceNotFoundException("Machine not found"));
        if (!machine.getTenantId().equals(tenantId.trim())) {
            throw new ResourceNotFoundException("Machine not found");
        }
    }

    private void validateRange(Instant from, Instant until, int page, int size) {
        if (from != null && until != null && from.isAfter(until)) {
            throw new InvalidOperationException("from must be earlier than or equal to until");
        }
        if (page < 0 || size < 1 || size > 500) {
            throw new InvalidOperationException("page must be non-negative and size must be between 1 and 500");
        }
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
    }
}
