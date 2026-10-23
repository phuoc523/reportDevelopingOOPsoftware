package com.scentstation;

import com.scentstation.MachineRequests.*;
import com.scentstation.MachineResponses.*;


import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SlotService {
    private final MachineService machineService;
    private final MachineSlotRepository slots;

    public SlotService(MachineService machineService, MachineSlotRepository slots) {
        this.machineService = machineService;
        this.slots = slots;
    }

    @Transactional
    public SlotResponse create(Long machineId, CreateSlotRequest request, String actorId) {
        Machine machine = machineService.requireMachine(machineId);
        if (slots.existsByMachineIdAndSlotNumber(machineId, request.slotNumber()))
            throw new ConflictException("slotNumber already exists for this machine");
        if (slots.existsByMachineIdAndSlotCodeIgnoreCase(machineId, request.slotCode().trim()))
            throw new ConflictException("slotCode already exists for this machine");
        if ((request.productId() == null || request.productId().isBlank())
                != (request.cartridgeId() == null || request.cartridgeId().isBlank())) {
            throw new InvalidOperationException("productId and cartridgeId must both be supplied or both be empty");
        }

        if (request.productId() != null && !request.productId().isBlank()
                && slots.existsByMachineIdAndCartridgeIdIgnoreCase(machineId, request.cartridgeId().trim())) {
            throw new ConflictException("This cartridge is already assigned to another slot on this machine");
        }
        MachineSlot slot = new MachineSlot();
        slot.setMachineId(machine.getId());
        slot.setSlotNumber(request.slotNumber());
        slot.setSlotCode(request.slotCode().trim());
        slot.setProductId(blankToNull(request.productId()));
        slot.setCartridgeId(blankToNull(request.cartridgeId()));
        slot.setEnabled(request.enabled());
        slot.setStockThreshold(request.stockThreshold());
        slot.setCalibratedSprayDurationMs(request.calibratedSprayDurationMs());
        slot.setDoseMl(request.doseMl());
        slot.setPrice(request.price());
        slot.setStatus(resolveStatus(slot));
        try {
            MachineSlot saved = slots.saveAndFlush(slot);
            machineService.saveHistory(machine, MachineHistoryAction.SLOT_CREATED,
                machine.getTenantId(), machine.getTenantId(), machine.getLocationId(),
                machine.getLocationId(), "Created slot " + saved.getSlotCode(), actorId);
            return toResponse(saved, machine);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("slotNumber or slotCode already exists for this machine");
        }
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> list(Long machineId) {
        Machine machine = machineService.requireMachine(machineId);
        return slots.findAllByMachineIdOrderBySlotNumberAsc(machineId).stream()
            .map(slot -> toResponse(slot, machine)).toList();
    }

    @Transactional(readOnly = true)
    public SlotResponse get(Long machineId, Long slotId) {
        Machine machine = machineService.requireMachine(machineId);
        return toResponse(requireSlot(machineId, slotId), machine);
    }

    @Transactional
    public SlotResponse update(Long machineId, Long slotId, UpdateSlotRequest request, String actorId) {
        Machine machine = machineService.requireMachine(machineId);
        MachineSlot slot = requireSlot(machineId, slotId);
        String oldProductId = slot.getProductId();
        String oldCartridgeId = slot.getCartridgeId();
        java.math.BigDecimal oldPrice = slot.getPrice();
        Integer oldStockThreshold = slot.getStockThreshold();
        Integer oldDuration = slot.getCalibratedSprayDurationMs();
        Double oldDoseMl = slot.getDoseMl();
        boolean oldEnabled = slot.isEnabled();

        if ((request.productId() == null || request.productId().isBlank())
                != (request.cartridgeId() == null || request.cartridgeId().isBlank())) {
            throw new InvalidOperationException("productId and cartridgeId must both be supplied or both be empty");
        }
        String nextCartridge = blankToNull(request.cartridgeId());
        if (nextCartridge != null && slots.existsByMachineIdAndCartridgeIdIgnoreCaseAndIdNot(machineId, nextCartridge, slotId)) {
            throw new ConflictException("This cartridge is already assigned to another slot on this machine");
        }
        slot.setProductId(blankToNull(request.productId()));
        slot.setCartridgeId(nextCartridge);
        slot.setEnabled(request.enabled());
        slot.setStockThreshold(request.stockThreshold());
        slot.setCalibratedSprayDurationMs(request.calibratedSprayDurationMs());
        slot.setDoseMl(request.doseMl());
        slot.setPrice(request.price());
        slot.setStatus(resolveStatus(slot));
        MachineSlot saved = slots.save(slot);

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
    public SlotResponse setEnabled(Long machineId, Long slotId, UpdateSlotEnabledRequest request, String actorId) {
        Machine machine = machineService.requireMachine(machineId);
        MachineSlot slot = requireSlot(machineId, slotId);
        slot.setEnabled(request.enabled());
        slot.setStatus(resolveStatus(slot));
        MachineSlot saved = slots.save(slot);
        machineService.saveHistory(machine, request.enabled() ? MachineHistoryAction.SLOT_ENABLED
                : MachineHistoryAction.SLOT_DISABLED,
            machine.getTenantId(), machine.getTenantId(), machine.getLocationId(),
            machine.getLocationId(), "Slot " + saved.getSlotCode() + " enabled=" + request.enabled(), actorId);
        return toResponse(saved, machine);
    }

    @Transactional
    public SlotResponse updateReportedStatus(Long machineId, Long slotId, SlotStatus status, String actorId) {
        Machine machine = machineService.requireMachine(machineId);
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
        return machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.ACTIVE
            && machine.getOperationalStatus() == MachineOperationalStatus.ONLINE
            && slot.isEnabled()
            && slot.getStatus() == SlotStatus.AVAILABLE
            && AvailabilityService.isWithinOperatingHours(machine.getOperatingHoursStart(), machine.getOperatingHoursEnd(), java.time.LocalTime.now())
            && !blank(slot.getProductId())
            && !blank(slot.getCartridgeId());
    }

    private SlotResponse toResponse(MachineSlot slot, Machine machine) {
        return SlotResponse.from(slot, isAvailable(machine, slot));
    }

    private MachineSlot requireSlot(Long machineId, Long slotId) {
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean equals(Object a, Object b) { return a == null ? b == null : a.equals(b); }
}
