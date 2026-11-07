package com.scentstation.service;

import com.scentstation.dto.request.CreateDispenseCommandRequest;
import com.scentstation.dto.response.DispenseCommandResponse;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineCommand;
import com.scentstation.entity.MachineCommandStatus;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.SlotStatus;
import com.scentstation.exception.ConflictException;
import com.scentstation.exception.InvalidOperationException;
import com.scentstation.exception.ResourceNotFoundException;
import com.scentstation.repository.MachineCommandRepository;
import com.scentstation.repository.MachineRepository;
import com.scentstation.repository.MachineSlotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.EnumSet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class DispenseCommandService {
    private final MachineCommandRepository commands;
    private final MachineRepository machines;
    private final MachineSlotRepository slots;
    private final AvailabilityService availabilityService;
    private final Duration commandLifetime;

    public DispenseCommandService(MachineCommandRepository commands, MachineRepository machines,
                                  MachineSlotRepository slots, AvailabilityService availabilityService,
                                  @Value("${machine.commands.lifetime-ms:60000}") long lifetimeMs) {
        this.commands = commands;
        this.machines = machines;
        this.slots = slots;
        this.availabilityService = availabilityService;
        this.commandLifetime = Duration.ofMillis(lifetimeMs);
    }

    @Transactional
    public DispenseCommandResponse create(CreateDispenseCommandRequest request) {
        var existing = commands.findByCommandId(request.commandId());
        String fingerprint = fingerprint(request);
        if (existing.isPresent()) {
            if (!fingerprint.equals(existing.get().getRequestFingerprint())) {
                throw new ConflictException("commandId is already used with a different request payload");
            }
            return toResponse(existing.get());
        }

        var activeStatuses = EnumSet.of(MachineCommandStatus.ACCEPTED,
            MachineCommandStatus.PROCESSING, MachineCommandStatus.ACKNOWLEDGED);
        if (commands.existsByOrderIdAndStatusIn(request.orderId(), activeStatuses)) {
            throw new ConflictException("Order already has an active dispensing command");
        }

        Long machineId = parseId(request.machineId(), "machineId");
        Long slotId = parseId(request.slotId(), "slotId");
        Machine machine = machines.findById(machineId)
            .orElseThrow(() -> new ResourceNotFoundException("Machine not found: " + request.machineId()));
        if (!machine.getTenantId().equals(request.tenantId())) {
            throw new InvalidOperationException("Tenant does not match the machine");
        }
        if (machine.getDeviceRegistrationStatus() != DeviceRegistrationStatus.ACTIVE) {
            throw new InvalidOperationException("Machine device registration is not active");
        }
        if (machine.getOperationalStatus() != MachineOperationalStatus.ONLINE) {
            throw new InvalidOperationException("Machine is not available: " + machine.getOperationalStatus());
        }
        slots.findByIdAndMachineId(slotId, machineId)
            .orElseThrow(() -> new ResourceNotFoundException("Slot not found on machine: " + request.slotId()));
        availabilityService.requireAvailableForDispense(machineId, slotId);

        Instant now = Instant.now();
        MachineCommand command = commands.save(new MachineCommand(request.commandId(), request.tenantId(),
            request.orderId(), request.paymentReference(), machineId, request.slotId(), fingerprint, now,
            now.plus(commandLifetime)));
        return toResponse(command);
    }

    @Transactional(readOnly = true)
    public DispenseCommandResponse get(String commandId, String tenantId) {
        MachineCommand command = commands.findByCommandId(commandId)
            .orElseThrow(() -> new ResourceNotFoundException("Dispensing command not found: " + commandId));
        if (tenantId != null && !tenantId.isBlank()
                && !command.getTenantId().equals(tenantId.trim())) {
            throw new InvalidOperationException("Dispensing command does not belong to the supplied tenant");
        }
        return toResponse(command);
    }

    private Long parseId(String value, String field) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new InvalidOperationException(field + " must be a numeric machine/slot identifier");
        }
    }

    private String fingerprint(CreateDispenseCommandRequest request) {
        String canonical = String.join("|", request.tenantId().trim(), request.orderId().trim(),
            request.paymentReference().trim(), request.machineId().trim(), request.slotId().trim(), request.commandId().trim());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private DispenseCommandResponse toResponse(MachineCommand command) {
        String message = switch (command.getStatus()) {
            case ACCEPTED -> "Dispensing command accepted";
            case PROCESSING, ACKNOWLEDGED -> "Dispensing is in progress";
            case DISPENSED -> "Dispensing completed successfully";
            case FAILED -> "Dispensing failed";
            case EXPIRED -> "Dispensing result is unknown";
            case UNKNOWN -> "Dispensing result is unknown";
        };
        return DispenseCommandResponse.of(command.getOrderId(), command.getCommandId(), command.getStatus(), message);
    }
}
