package com.scentstation.service;

import com.scentstation.dto.request.MachineRequests.*;
import com.scentstation.dto.response.MachineResponses.*;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Location;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineHistory;
import com.scentstation.entity.MachineHistoryAction;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.entity.MachineSlot;
import com.scentstation.entity.SlotStatus;
import com.scentstation.event.MachineAuditEvent;
import com.scentstation.exception.ConflictException;
import com.scentstation.exception.InvalidOperationException;
import com.scentstation.exception.ResourceNotFoundException;
import com.scentstation.repository.LocationRepository;
import com.scentstation.repository.MachineHistoryRepository;
import com.scentstation.repository.MachineRepository;
import com.scentstation.repository.MachineSlotRepository;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@Service
public class MachineService {
    private final MachineRepository machines;
    private final MachineSlotRepository slots;
    private final MachineHistoryRepository history;
    private final LocationRepository locations;
    private final ApplicationEventPublisher events;
    private final MachineAlertService machineAlerts;

    public MachineService(MachineRepository machines, MachineSlotRepository slots,
                          MachineHistoryRepository history, LocationRepository locations,
                          ApplicationEventPublisher events, MachineAlertService machineAlerts) {
        this.machines = machines; this.slots = slots; this.history = history;
        this.locations = locations; this.events = events; this.machineAlerts = machineAlerts;
    }

    private void validateLocation(@Nullable String locationId, @Nullable String tenantId) {
        if (locationId == null || locationId.isBlank()) {
            throw new InvalidOperationException("locationId is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new InvalidOperationException("tenantId is required");
        }
        String normalizedLocationId = Objects.requireNonNull(locationId.trim(), "locationId");
        Location location = locations.findById(
            Objects.requireNonNull(normalizedLocationId, "locationId")).orElseThrow(() ->
            new ResourceNotFoundException("Location not found: " + locationId));
        if (!location.isActive()) throw new InvalidOperationException("Location is inactive");
        if (!location.getTenantId().equals(tenantId.trim()))
            throw new InvalidOperationException("Location does not belong to the selected Tenant");
    }

    // P4-01: register a device and its initial Tenant/location assignment.
    @Transactional
    public MachineResponse register(CreateMachineRequest request) {
        String serial = request.getSerialNumber().trim();
        String identity = request.getDeviceIdentity().trim();

        validateLocation(request.getLocationId(), request.getTenantId());
        if (machines.existsBySerialNumberIgnoreCase(serial))
            throw new ConflictException("serialNumber already exists");
        if (machines.existsByDeviceIdentityIgnoreCase(identity))
            throw new ConflictException("deviceIdentity already exists");

        Machine machine = new Machine();
        machine.setSerialNumber(serial);
        machine.setDeviceIdentity(identity);
        machine.setTenantId(request.getTenantId().trim());
        machine.setLocationId(request.getLocationId().trim());
        machine.setName(request.getName().trim());

        try {
            Machine saved = machines.saveAndFlush(machine);
            saveHistory(saved, MachineHistoryAction.REGISTERED, null, saved.getTenantId(),
                null, saved.getLocationId(), "Machine registered", null);
            return MachineResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("serialNumber or deviceIdentity already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<MachineResponse> list(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new InvalidOperationException("tenantId is required to list machines; cross-tenant listing is not allowed");
        }
        List<Machine> result = machines.findAllByTenantIdOrderByIdAsc(tenantId.trim());
        return result.stream().map(MachineResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MachineResponse get(@NonNull Long id) {
        return MachineResponse.from(requireMachine(id));
    }

    @Transactional
    public MachineResponse update(@NonNull Long id, UpdateMachineRequest request, String actorId) {
        Machine machine = requireMachine(id);
        String oldTenant = machine.getTenantId();
        String oldLocation = machine.getLocationId();
        String oldName = machine.getName();
        validateLocation(request.getLocationId(), request.getTenantId());
        if (machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.REVOKED) {
            throw new InvalidOperationException("A revoked device cannot be updated; reactivate it first");
        }

        machine.setTenantId(request.getTenantId().trim());
        machine.setLocationId(request.getLocationId().trim());
        machine.setName(request.getName().trim());
        if (!oldTenant.equals(request.getTenantId().trim())) {
            // Prevent product/cartridge assignments from leaking across Tenant ownership.
            for (MachineSlot slot : slots.findAllByMachineIdOrderBySlotNumberAsc(id)) {
                slot.setProductId(null);
                slot.setCartridgeId(null);
                slot.setStatus(slot.isEnabled() ? SlotStatus.EMPTY : SlotStatus.DISABLED);
            }
            slots.flush();
        }
        Machine saved = machines.save(machine);

        boolean tenantChanged = !oldTenant.equals(saved.getTenantId());
        boolean locationChanged = !oldLocation.equals(saved.getLocationId());
        if (tenantChanged) {
            saveHistory(saved, MachineHistoryAction.TENANT_CHANGED, oldTenant, saved.getTenantId(),
                oldLocation, saved.getLocationId(), "Tenant assignment changed", actorId);
        }
        if (locationChanged) {
            saveHistory(saved, MachineHistoryAction.LOCATION_CHANGED, oldTenant, saved.getTenantId(),
                oldLocation, saved.getLocationId(), "Machine location changed", actorId);
        }
        if (!tenantChanged && !locationChanged && !oldName.equals(saved.getName())) {
            saveHistory(saved, MachineHistoryAction.DETAILS_UPDATED, oldTenant, saved.getTenantId(),
                oldLocation, saved.getLocationId(), "Machine name updated", actorId);
        }
        return MachineResponse.from(saved);
    }

    @Transactional
    public MachineResponse updateDeviceRegistrationStatus(@NonNull Long id,
            UpdateDeviceRegistrationStatusRequest request, String actorId) {
        Machine machine = requireMachine(id);
        DeviceRegistrationStatus old = machine.getDeviceRegistrationStatus();
        DeviceRegistrationStatus next = request.getStatus();
        machine.setDeviceRegistrationStatus(next);

        // A revoked device must not remain operationally available.
        if (next == DeviceRegistrationStatus.REVOKED) {
            machine.setOperationalStatus(MachineOperationalStatus.DISABLED);
        } else if (old == DeviceRegistrationStatus.REVOKED
                && machine.getOperationalStatus() == MachineOperationalStatus.DISABLED) {
            // Re-activation only restores registration; heartbeat must establish ONLINE state.
            machine.setOperationalStatus(MachineOperationalStatus.OFFLINE);
        }
        Machine saved = machines.save(machine);
        if (old != next) {
            MachineHistoryAction action = next == DeviceRegistrationStatus.REVOKED
                ? MachineHistoryAction.DEVICE_REVOKED : MachineHistoryAction.DEVICE_ACTIVATED;
            saveHistory(saved, action, saved.getTenantId(), saved.getTenantId(),
                saved.getLocationId(), saved.getLocationId(),
                "Device registration status changed from " + old + " to " + next, actorId);
        }
        return MachineResponse.from(saved);
    }

    @Transactional
    public MachineResponse updateOperationalStatus(@NonNull Long id,
            UpdateOperationalStatusRequest request, String actorId) {
        Machine machine = requireMachine(id);
        if (machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.REVOKED
                && request.getStatus() != MachineOperationalStatus.DISABLED) {
            throw new InvalidOperationException("A revoked device cannot be set to an operational status");
        }
        MachineOperationalStatus old = machine.getOperationalStatus();
        MachineOperationalStatus next = request.getStatus();
        if (machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.REVOKED
                && next != MachineOperationalStatus.DISABLED) {
            throw new InvalidOperationException("A revoked device must remain DISABLED");
        }
        if (next == MachineOperationalStatus.ONLINE
                && (old == MachineOperationalStatus.MAINTENANCE || old == MachineOperationalStatus.ERROR)) {
            throw new InvalidOperationException("A machine in MAINTENANCE/ERROR must be checked and enabled before returning ONLINE");
        }
        if (old == MachineOperationalStatus.DISABLED && next != MachineOperationalStatus.DISABLED) {
            throw new InvalidOperationException("A disabled machine must be re-enabled through the control endpoint");
        }
        machine.setOperationalStatus(next);
        Machine saved = machines.save(machine);
        if (old != request.getStatus()) {
            saveHistory(saved, MachineHistoryAction.OPERATIONAL_STATUS_CHANGED,
                saved.getTenantId(), saved.getTenantId(), saved.getLocationId(),
                saved.getLocationId(), "Operational status changed from " + old + " to "
                    + request.getStatus(), actorId);
        }
        return MachineResponse.from(saved);
    }

    @Transactional
    public MachineResponse control(@NonNull Long id, MachineControlRequest request, String actorId) {
        Machine machine = requireMachine(id);
        MachineOperationalStatus old = machine.getOperationalStatus();
        MachineOperationalStatus next;
        switch (request.getAction()) {
            case ENABLE -> {
                if (machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.REVOKED)
                    throw new InvalidOperationException("Cannot enable a revoked device");
                if (old == MachineOperationalStatus.MAINTENANCE || old == MachineOperationalStatus.ERROR)
                    throw new InvalidOperationException("Resolve maintenance/error before enabling the machine");
                // Re-enable a disabled machine as OFFLINE; otherwise preserve its current state.
                // Enable does not imply that a device is online; heartbeat must confirm it.
                next = old == MachineOperationalStatus.DISABLED
                    ? MachineOperationalStatus.OFFLINE : old;
            }
            case DISABLE -> next = MachineOperationalStatus.DISABLED;
            case ENTER_MAINTENANCE -> {
                if (machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.REVOKED)
                    throw new InvalidOperationException("Cannot control a revoked device");
                next = MachineOperationalStatus.MAINTENANCE;
            }
            default -> throw new InvalidOperationException("Unsupported machine control action");
        }
        machine.setOperationalStatus(next);
        Machine saved = machines.save(machine);
        if (old != next) {
            MachineHistoryAction action = next == MachineOperationalStatus.DISABLED
                ? MachineHistoryAction.MACHINE_DISABLED
                : next == MachineOperationalStatus.MAINTENANCE
                    ? MachineHistoryAction.OPERATIONAL_STATUS_CHANGED
                    : MachineHistoryAction.MACHINE_ENABLED;
            saveHistory(saved, action, saved.getTenantId(), saved.getTenantId(),
                saved.getLocationId(), saved.getLocationId(),
                "Machine control action " + request.getAction() + ": " + old + " -> " + next, actorId);
        }
        return MachineResponse.from(saved);
    }

    @Transactional
    public MachineResponse updateOperatingHours(@NonNull Long id, UpdateOperatingHoursRequest request, String actorId) {
        if ((request.getStartTime() == null) != (request.getEndTime() == null)) {
            throw new InvalidOperationException("Both operating-hours values must be supplied, or both must be null to clear the schedule");
        }
        if (request.getStartTime() != null && request.getStartTime().equals(request.getEndTime())) {
            throw new InvalidOperationException("Operating-hours start and end times cannot be equal");
        }
        Machine machine = requireMachine(id);
        LocalTime oldStart = machine.getOperatingHoursStart();
        LocalTime oldEnd = machine.getOperatingHoursEnd();
        machine.setOperatingHoursStart(request.getStartTime());
        machine.setOperatingHoursEnd(request.getEndTime());
        Machine saved = machines.save(machine);
        saveHistory(saved, MachineHistoryAction.OPERATING_HOURS_CHANGED,
            saved.getTenantId(), saved.getTenantId(), saved.getLocationId(), saved.getLocationId(),
            "Operating hours changed from " + oldStart + "-" + oldEnd + " to "
                + (request.getStartTime() == null ? "24/7" : request.getStartTime() + "-" + request.getEndTime()), actorId);
        return MachineResponse.from(saved);
    }


    @Transactional
    public MachineResponse heartbeat(@NonNull Long id) {
        Machine machine = requireMachine(id);
        if (machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.REVOKED) {
            throw new InvalidOperationException("Revoked device cannot send heartbeat");
        }
        java.time.Instant now = java.time.Instant.now();
        machine.setLastSeenAt(now);
        if (machine.getOperationalStatus() == MachineOperationalStatus.OFFLINE
                || machine.getOperationalStatus() == MachineOperationalStatus.UNSTABLE) {
            machine.setOperationalStatus(MachineOperationalStatus.ONLINE);
        }
        Machine saved = machines.save(machine);
        machineAlerts.resolveMachineOfflineAlerts(saved, now);
        return MachineResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MachineHistoryResponse> history(@NonNull Long id) {
        requireMachine(id);
        return history.findAllByMachineIdOrderByOccurredAtDesc(id)
            .stream().map(MachineHistoryResponse::from).toList();
    }

    public Machine requireMachine(@NonNull Long id) {
        Long requiredId = Objects.requireNonNull(id, "id");
        return machines.findById(requiredId).orElseThrow(() ->
            new ResourceNotFoundException("Machine not found: " + id));
    }

    public void saveHistory(Machine machine, MachineHistoryAction action, String oldTenant,
                     String newTenant, String oldLocation, String newLocation,
                     String details, String actorId) {
        history.save(new MachineHistory(machine.getId(), action, oldTenant, newTenant,
            oldLocation, newLocation, details, actorId));
        events.publishEvent(new MachineAuditEvent(machine.getId(), action.name(), actorId, details, java.time.Instant.now()));
    }
}
