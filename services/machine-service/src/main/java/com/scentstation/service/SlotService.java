package com.scentstation.service;

import com.scentstation.dto.request.MachineRequests.*;
import com.scentstation.dto.response.MachineResponses.*;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineAlert;
import com.scentstation.entity.MachineAlertStatus;
import com.scentstation.entity.MachineHistoryAction;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.entity.MachineSlot;
import com.scentstation.entity.SlotStatus;
import com.scentstation.exception.ConflictException;
import com.scentstation.exception.InvalidOperationException;
import com.scentstation.exception.ResourceNotFoundException;
import com.scentstation.repository.MachineSlotRepository;
import com.scentstation.repository.MachineAlertRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;
import java.util.List;
import java.util.EnumSet;

@Service
public class SlotService {
    private final MachineService machineService;
    private final MachineSlotRepository slots;
    private final MachineAlertRepository alerts;
    private final ZoneId operatingHoursZone;
    private final OperationServiceCatalogValidator catalogValidator;

    public SlotService(MachineService machineService, MachineSlotRepository slots,
                       MachineAlertRepository alerts, OperationServiceCatalogValidator catalogValidator,
                       @Value("${machine.operating-hours.zone:Asia/Ho_Chi_Minh}") String operatingHoursZone) {
        this.machineService = machineService;
        this.slots = slots;
        this.alerts = alerts;
        this.catalogValidator = catalogValidator;
        this.operatingHoursZone = ZoneId.of(operatingHoursZone);
    }

    @Transactional
    public SlotResponse create(@NonNull Long machineId, CreateSlotRequest request, String actorId) {
        Machine machine = machineService.requireMachine(machineId);
        ensureDeviceActive(machine);
        if (slots.existsByMachineIdAndSlotNumber(machineId, request.getSlotNumber()))
            throw new ConflictException("slotNumber already exists for this machine");
        if (slots.existsByMachineIdAndSlotCodeIgnoreCase(machineId, request.getSlotCode().trim()))
            throw new ConflictException("slotCode already exists for this machine");
        if ((request.getProductId() == null || request.getProductId().isBlank())
                != (request.getCartridgeId() == null || request.getCartridgeId().isBlank())) {
            throw new InvalidOperationException("productId and cartridgeId must both be supplied or both be empty");
        }

        if (request.getProductId() != null && !request.getProductId().isBlank()) {
            catalogValidator.validateReferences(request.getProductId().trim(), request.getCartridgeId().trim(), machine.getTenantId());
        }
        if (request.getProductId() != null && !request.getProductId().isBlank()
                && slots.existsByCartridgeIdIgnoreCase(request.getCartridgeId().trim())) {
            throw new ConflictException("This cartridge is already assigned to another slot");
        }
        MachineSlot slot = new MachineSlot();
        slot.setMachineId(machine.getId());
        slot.setSlotNumber(request.getSlotNumber());
        slot.setSlotCode(request.getSlotCode().trim());
        slot.setProductId(blankToNull(request.getProductId()));
        slot.setCartridgeId(blankToNull(request.getCartridgeId()));
        slot.setEnabled(request.getEnabled());
        slot.setStockThreshold(request.getStockThreshold());
        slot.setCalibratedSprayDurationMs(request.getCalibratedSprayDurationMs());
        slot.setDoseMl(request.getDoseMl());
        slot.setPrice(request.getPrice());
        slot.setStatus(resolveStatus(slot));
        try {
            MachineSlot saved = slots.saveAndFlush(slot);
            machineService.saveHistory(machine, MachineHistoryAction.SLOT_CREATED,
                machine.getTenantId(), machine.getTenantId(), machine.getLocationId(),
                machine.getLocationId(), "Created slot " + saved.getSlotCode(), actorId);
            return toResponse(saved, machine);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("slotNumber or slotCode already exists for this machine, or the cartridge is assigned elsewhere");
        }
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> list(@NonNull Long machineId) {
        Machine machine = machineService.requireMachine(machineId);
        return slots.findAllByMachineIdOrderBySlotNumberAsc(machineId).stream()
            .map(slot -> toResponse(slot, machine)).toList();
    }

    @Transactional(readOnly = true)
    public SlotResponse get(@NonNull Long machineId, @NonNull Long slotId) {
        Machine machine = machineService.requireMachine(machineId);
        return toResponse(requireSlot(machineId, slotId), machine);
    }

    @Transactional
    public SlotResponse update(@NonNull Long machineId, @NonNull Long slotId,
                               UpdateSlotRequest request, String actorId) {
        Machine machine = machineService.requireMachine(machineId);
        ensureDeviceActive(machine);
        MachineSlot slot = requireSlot(machineId, slotId);
        String oldProductId = slot.getProductId();
        String oldCartridgeId = slot.getCartridgeId();
        java.math.BigDecimal oldPrice = slot.getPrice();
        Integer oldStockThreshold = slot.getStockThreshold();
        Integer oldDuration = slot.getCalibratedSprayDurationMs();
        Double oldDoseMl = slot.getDoseMl();
        boolean oldEnabled = slot.isEnabled();

        if ((request.getProductId() == null || request.getProductId().isBlank())
                != (request.getCartridgeId() == null || request.getCartridgeId().isBlank())) {
            throw new InvalidOperationException("productId and cartridgeId must both be supplied or both be empty");
        }
        String nextCartridge = blankToNull(request.getCartridgeId());
        if (request.getProductId() != null && !request.getProductId().isBlank()) {
            catalogValidator.validateReferences(request.getProductId().trim(), nextCartridge, machine.getTenantId());
        }
        if (nextCartridge != null && slots.existsByCartridgeIdIgnoreCaseAndIdNot(nextCartridge, slotId)) {
            throw new ConflictException("This cartridge is already assigned to another slot");
        }
        slot.setProductId(blankToNull(request.getProductId()));
        slot.setCartridgeId(nextCartridge);
        slot.setEnabled(request.getEnabled());
        slot.setStockThreshold(request.getStockThreshold());
        slot.setCalibratedSprayDurationMs(request.getCalibratedSprayDurationMs());
        slot.setDoseMl(request.getDoseMl());
        slot.setPrice(request.getPrice());
        slot.setStatus(resolveStatus(slot));
        MachineSlot saved;
        try {
            saved = slots.saveAndFlush(slot);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                "Slot configuration violates a uniqueness constraint (slot number, slot code, or cartridge assignment)"
            );
        }

        if (!equals(oldProductId, saved.getProductId()) || !equals(oldCartridgeId, saved.getCartridgeId())) {
            machineService.saveHistory(machine, MachineHistoryAction.SLOT_PRODUCT_CHANGED,
                machine.getTenantId(), machine.getTenantId(), machine.getLocationId(),
                machine.getLocationId(), "Slot " + saved.getSlotCode() + " product/cartridge assignment changed", actorId);
        }
        if (oldEnabled != saved.isEnabled()) {
            machineService.saveHistory(machine, saved.isEnabled() ? MachineHistoryAction.SLOT_ENABLED
                    : MachineHistoryAction.SLOT_DISABLED,
                machine.getTenantId(), machine.getTenantId(), machine.getLocationId(),
                machine.getLocationId(), "Slot " + saved.getSlotCode() + " enabled=" + saved.isEnabled(), actorId);
        }
        if (oldPrice.compareTo(saved.getPrice()) != 0) {
            machineService.saveHistory(machine, MachineHistoryAction.SLOT_UPDATED,
                machine.getTenantId(), machine.getTenantId(), machine.getLocationId(), machine.getLocationId(),
                "Slot " + saved.getSlotCode() + " price changed from " + oldPrice + " to " + saved.getPrice(), actorId);
        }
        if (oldEnabled == saved.isEnabled()
                && equals(oldProductId, saved.getProductId())
                && equals(oldCartridgeId, saved.getCartridgeId())
                && oldPrice.compareTo(saved.getPrice()) == 0
                && java.util.Objects.equals(oldStockThreshold, saved.getStockThreshold())
                && java.util.Objects.equals(oldDuration, saved.getCalibratedSprayDurationMs())
                && java.util.Objects.equals(oldDoseMl, saved.getDoseMl())) {
            machineService.saveHistory(machine, MachineHistoryAction.SLOT_UPDATED,
                machine.getTenantId(), machine.getTenantId(), machine.getLocationId(),
                machine.getLocationId(), "Slot configuration update requested with no effective value change: " + saved.getSlotCode(), actorId);
        } else if (!java.util.Objects.equals(oldStockThreshold, saved.getStockThreshold())
                || !java.util.Objects.equals(oldDuration, saved.getCalibratedSprayDurationMs())
                || !java.util.Objects.equals(oldDoseMl, saved.getDoseMl())) {
            machineService.saveHistory(machine, MachineHistoryAction.SLOT_UPDATED,
                machine.getTenantId(), machine.getTenantId(), machine.getLocationId(), machine.getLocationId(),
                "Slot " + saved.getSlotCode() + " calibration/stock changed: stockThreshold " + oldStockThreshold + " -> " + saved.getStockThreshold()
                    + ", sprayDurationMs " + oldDuration + " -> " + saved.getCalibratedSprayDurationMs()
                    + ", doseMl " + oldDoseMl + " -> " + saved.getDoseMl(), actorId);
        }
        return toResponse(saved, machine);
    }

    @Transactional
    public SlotResponse setEnabled(@NonNull Long machineId, @NonNull Long slotId,
                                   UpdateSlotEnabledRequest request, String actorId) {
        Machine machine = machineService.requireMachine(machineId);
        ensureDeviceActive(machine);
        MachineSlot slot = requireSlot(machineId, slotId);
        slot.setEnabled(request.getEnabled());
        slot.setStatus(resolveStatus(slot));
        MachineSlot saved = slots.save(slot);
        machineService.saveHistory(machine, request.getEnabled() ? MachineHistoryAction.SLOT_ENABLED
                : MachineHistoryAction.SLOT_DISABLED,
            machine.getTenantId(), machine.getTenantId(), machine.getLocationId(),
            machine.getLocationId(), "Slot " + saved.getSlotCode() + " enabled=" + request.getEnabled(), actorId);
        return toResponse(saved, machine);
    }

    @Transactional
    public SlotResponse updateReportedStatus(@NonNull Long machineId, @NonNull Long slotId,
                                             SlotStatus status, String actorId) {
        Machine machine = machineService.requireMachine(machineId);
        ensureDeviceActive(machine);
        MachineSlot slot = requireSlot(machineId, slotId);
        if (status == SlotStatus.AVAILABLE && !slot.isEnabled()) {
            throw new InvalidOperationException("A disabled slot cannot be set to AVAILABLE");
        }
        if (status == SlotStatus.AVAILABLE && (blank(slot.getProductId()) || blank(slot.getCartridgeId()))) {
            throw new InvalidOperationException("A slot without both productId and cartridgeId cannot be AVAILABLE");
        }
        SlotStatus old = slot.getStatus();
        slot.setStatus(status);
        MachineSlot saved = slots.save(slot);
        if (old != status) {
            machineService.saveHistory(machine, MachineHistoryAction.SLOT_STATUS_CHANGED,
                machine.getTenantId(), machine.getTenantId(), machine.getLocationId(),
                machine.getLocationId(), "Slot " + saved.getSlotCode() + " status changed from " + old + " to " + status, actorId);
        }
        return toResponse(saved, machine);
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(Machine machine, MachineSlot slot) {
        return isAvailable(machine, slot, OperatingHours.currentTime(operatingHoursZone),
            activeAlertReason(machine, slot));
    }

    boolean isAvailable(Machine machine, MachineSlot slot, java.time.LocalTime now) {
        return isAvailable(machine, slot, now, activeAlertReason(machine, slot));
    }

    private boolean isAvailable(Machine machine, MachineSlot slot, java.time.LocalTime now,
                               String activeAlertReason) {
        return machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.ACTIVE
            && machine.getOperationalStatus() == MachineOperationalStatus.ONLINE
            && activeAlertReason == null
            && slot.isEnabled()
            && slot.getStatus() == SlotStatus.AVAILABLE
            && OperatingHours.includes(machine.getOperatingHoursStart(),
                machine.getOperatingHoursEnd(), now)
            && !blank(slot.getProductId())
            && !blank(slot.getCartridgeId());
    }

    String availabilityReason(Machine machine, MachineSlot slot, java.time.LocalTime now) {
        return availabilityReason(machine, slot, now, activeAlertReason(machine, slot));
    }

    String availabilityReason(Machine machine, MachineSlot slot, java.time.LocalTime now,
                              String activeAlertReason) {
        if (machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.REVOKED) {
            return "Device registration has been revoked";
        }
        if (machine.getOperationalStatus() != MachineOperationalStatus.ONLINE) {
            return "Machine operational status is " + machine.getOperationalStatus();
        }
        if (!OperatingHours.includes(
                machine.getOperatingHoursStart(), machine.getOperatingHoursEnd(), now)) {
            return "Machine is outside operating hours";
        }
        if (activeAlertReason != null) {
            return activeAlertReason;
        }
        if (!slot.isEnabled()) {
            return "Slot is disabled";
        }
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            return "Slot status is " + slot.getStatus();
        }
        if (blank(slot.getProductId())) {
            return "No product is assigned to the slot";
        }
        if (blank(slot.getCartridgeId())) {
            return "No cartridge is assigned to the slot";
        }
        return null;
    }

    private SlotResponse toResponse(MachineSlot slot, Machine machine) {
        return toAvailabilityResponse(machine, slot, OperatingHours.currentTime(operatingHoursZone));
    }

    SlotResponse toAvailabilityResponse(Machine machine, MachineSlot slot, java.time.LocalTime now) {
        return toAvailabilityResponse(machine, slot, now, activeAlertReason(machine, slot));
    }

    SlotResponse toAvailabilityResponse(Machine machine, MachineSlot slot, java.time.LocalTime now,
                                        String activeAlertReason) {
        String reason = availabilityReason(machine, slot, now, activeAlertReason);
        return SlotResponse.from(slot, reason == null, reason);
    }

    private String activeAlertReason(Machine machine, MachineSlot slot) {
        List<MachineAlert> activeAlerts = alerts.findAllByMachineIdAndStatusInOrderByLastSeenAtDesc(
            machine.getId(), EnumSet.of(MachineAlertStatus.OPEN, MachineAlertStatus.ACKNOWLEDGED,
                MachineAlertStatus.ASSIGNED, MachineAlertStatus.ESCALATED));
        return activeAlerts.stream()
            .filter(alert -> alert.getSlotId() == null || alert.getSlotId().equals(slot.getId()))
            .findFirst()
            .map(alert -> "Active " + alert.getType() + " alert: " + alert.getSummary())
            .orElse(null);
    }

    private MachineSlot requireSlot(@NonNull Long machineId, @NonNull Long slotId) {
        return slots.findByIdAndMachineId(slotId, machineId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Slot " + slotId + " not found for machine " + machineId));
    }

    private SlotStatus resolveStatus(MachineSlot slot) {
        if (!slot.isEnabled()) return SlotStatus.DISABLED;
        if (blank(slot.getProductId()) || blank(slot.getCartridgeId())) return SlotStatus.EMPTY;
        if (slot.getStatus() == SlotStatus.ERROR) return SlotStatus.ERROR;
        return SlotStatus.AVAILABLE;
    }

    private void ensureDeviceActive(Machine machine) {
        if (machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.REVOKED) {
            throw new InvalidOperationException("A revoked device's slots cannot be changed");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean equals(Object a, Object b) { return a == null ? b == null : a.equals(b); }
}
