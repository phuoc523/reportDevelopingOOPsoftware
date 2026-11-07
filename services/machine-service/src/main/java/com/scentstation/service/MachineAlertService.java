package com.scentstation.service;

import com.scentstation.entity.Machine;
import com.scentstation.entity.AlertHandoffOutbox;
import com.scentstation.entity.MachineAlert;
import com.scentstation.entity.MachineAlertSeverity;
import com.scentstation.entity.MachineAlertSource;
import com.scentstation.entity.MachineAlertStatus;
import com.scentstation.entity.MachineAlertType;
import com.scentstation.entity.MachineSlot;
import com.scentstation.event.MachineAlertChangedEvent;
import com.scentstation.dto.request.LowStockAlertRequest;
import com.scentstation.exception.InvalidOperationException;
import com.scentstation.exception.ResourceNotFoundException;
import com.scentstation.repository.AlertHandoffOutboxRepository;
import com.scentstation.repository.MachineAlertRepository;
import com.scentstation.repository.MachineRepository;
import com.scentstation.repository.MachineSlotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class MachineAlertService {
    private static final Set<MachineAlertStatus> ACTIVE_STATUSES =
        EnumSet.of(MachineAlertStatus.OPEN, MachineAlertStatus.ACKNOWLEDGED,
            MachineAlertStatus.ASSIGNED, MachineAlertStatus.ESCALATED);

    private final MachineAlertRepository alerts;
    private final MachineRepository machines;
    private final MachineSlotRepository slots;
    private final ApplicationEventPublisher events;
    private final AlertHandoffOutboxRepository handoffOutbox;
    private final String notificationUrl;
    private final String maintenanceUrl;

    public MachineAlertService(MachineAlertRepository alerts, MachineRepository machines,
                               MachineSlotRepository slots, ApplicationEventPublisher events,
                               AlertHandoffOutboxRepository handoffOutbox,
                               @Value("${machine.integration.notification-service-url:}") String notificationUrl,
                               @Value("${machine.integration.maintenance-service-url:}") String maintenanceUrl) {
        this.alerts = alerts;
        this.machines = machines;
        this.slots = slots;
        this.events = events;
        this.handoffOutbox = handoffOutbox;
        this.notificationUrl = notificationUrl;
        this.maintenanceUrl = maintenanceUrl;
    }

    @Transactional
    public MachineAlert record(Machine machine, MachineAlertType type, MachineAlertSeverity severity,
                               Long slotId, String summary, Instant occurredAt) {
        return record(machine, type, severity, MachineAlertSource.DEVICE, slotId, summary, occurredAt);
    }

    @Transactional
    public MachineAlert record(Machine machine, MachineAlertType type, MachineAlertSeverity severity,
                               MachineAlertSource source, Long slotId, String summary,
                               Instant occurredAt) {
        machine = machines.findByIdForAlertUpdate(machine.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Machine not found"));
        if (summary == null || summary.isBlank() || summary.length() > 500) {
            throw new InvalidOperationException("Alert summary must contain between 1 and 500 characters");
        }
        if (slotId != null && slots.findByIdAndMachineId(slotId, machine.getId()).isEmpty()) {
            throw new InvalidOperationException("Alert slot does not belong to the machine");
        }
        String dedupeKey = machine.getId() + ":" + type + ":"
            + (slotId == null ? "machine" : slotId)
            + (type == MachineAlertType.LOW_STOCK ? ":" + source : "");
        MachineAlert alert = alerts.findFirstByDedupeKeyAndStatusInOrderByFirstSeenAtDesc(
            dedupeKey, ACTIVE_STATUSES).orElse(null);
        Instant eventTime = occurredAt == null ? Instant.now() : occurredAt;
        if (alert == null) {
            alert = alerts.save(new MachineAlert(dedupeKey, type, severity, source, machine, slotId,
                summary, eventTime));
            publish(alert);
        } else {
            boolean changed = alert.observeAgain(severity, summary, eventTime);
            alerts.save(alert);
            if (changed) {
                publish(alert);
            }
        }
        return alert;
    }

    @Transactional
    public MachineAlert recordLowStock(LowStockAlertRequest request) {
        Machine machine = machines.findByIdForAlertUpdate(request.getMachineId())
            .orElseThrow(() -> new ResourceNotFoundException("Machine not found"));
        if (!machine.getTenantId().equals(request.getTenantId().trim())) {
            throw new InvalidOperationException("Machine does not belong to the supplied tenant");
        }
        MachineSlot slot = slots.findByIdAndMachineId(request.getSlotId(), machine.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Slot not found for machine"));
        if (slot.isEnabled() && slot.getProductId() == null) {
            throw new InvalidOperationException("An enabled slot without a product cannot report low stock");
        }
        return record(machine, MachineAlertType.LOW_STOCK, MachineAlertSeverity.MEDIUM,
            MachineAlertSource.INVENTORY,
            slot.getId(), request.getSummary() == null || request.getSummary().isBlank()
                ? "Low stock reported by inventory service" : request.getSummary().trim(),
            Instant.now());
    }

    @Transactional(readOnly = true)
    public List<MachineAlert> list(String tenantId, MachineAlertStatus status,
                                   MachineAlertSeverity severity) {
        String requiredTenantId = requireTenantId(tenantId);
        List<MachineAlert> result = alerts.findAllByTenantIdOrderByLastSeenAtDesc(requiredTenantId);
        return result.stream()
            .filter(alert -> status == null || alert.getStatus() == status)
            .filter(alert -> severity == null || alert.getSeverity() == severity)
            .toList();
    }

    @Transactional(readOnly = true)
    public MachineAlert get(Long alertId, String tenantId) {
        return alerts.findByIdAndTenantId(alertId, requireTenantId(tenantId))
            .orElseThrow(() -> new ResourceNotFoundException("Machine alert not found"));
    }

    @Transactional
    public MachineAlert acknowledge(Long alertId, String tenantId, String actorId) {
        MachineAlert alert = requireActiveAlert(alertId, tenantId);
        if (alert.getAcknowledgedAt() == null) {
            alert.acknowledge(requireActorId(actorId), Instant.now());
            return saveAndPublish(alert);
        }
        return alert;
    }

    @Transactional
    public MachineAlert assign(Long alertId, String tenantId, String assigneeId) {
        MachineAlert alert = requireActiveAlert(alertId, tenantId);
        if (assigneeId == null || assigneeId.isBlank()) {
            throw new InvalidOperationException("assigneeId is required");
        }
        alert.assign(assigneeId.trim(), Instant.now());
        return saveAndPublish(alert);
    }

    @Transactional
    public MachineAlert resolve(Long alertId, String tenantId, String actorId, String notes) {
        MachineAlert alert = requireActiveAlert(alertId, tenantId);
        alert.resolve(requireActorId(actorId), notes, Instant.now());
        return saveAndPublish(alert);
    }

    @Transactional
    public MachineAlert reopen(Long alertId, String tenantId, String notes) {
        MachineAlert alert = requireAlert(alertId, tenantId);
        if (alert.getStatus() != MachineAlertStatus.RESOLVED) {
            throw new InvalidOperationException("Only a resolved alert can be reopened");
        }
        alert.reopen(notes, Instant.now());
        return saveAndPublish(alert);
    }

    @Transactional
    public void resolveMachineOfflineAlerts(Machine machine, Instant now) {
        resolveObservedAlert(machine, MachineAlertType.MACHINE_OFFLINE, null,
            MachineAlertSource.MONITOR, "Machine connectivity restored", now);
    }

    @Transactional
    public void resolveObservedAlert(Machine machine, MachineAlertType type, Long slotId,
                                    String notes, Instant now) {
        resolveObservedAlert(machine, type, slotId, MachineAlertSource.DEVICE, notes, now);
    }

    @Transactional
    public void resolveObservedAlert(Machine machine, MachineAlertType type, Long slotId,
                                    MachineAlertSource source, String notes, Instant now) {
        List<MachineAlert> matching = slotId == null
            ? alerts.findAllByMachineIdAndSlotIdIsNullAndTypeAndSourceAndStatusIn(
                machine.getId(), type, source, ACTIVE_STATUSES)
            : alerts.findAllByMachineIdAndSlotIdAndTypeAndSourceAndStatusIn(
                machine.getId(), slotId, type, source, ACTIVE_STATUSES);
        for (MachineAlert alert : matching) {
            alert.resolve("machine-service", notes, now);
            saveAndPublish(alert);
        }
    }

    @Transactional
    public void escalateOverdueAlerts(Duration escalationDelay, Instant now) {
        if (escalationDelay.isZero() || escalationDelay.isNegative()) {
            throw new IllegalArgumentException("Alert escalation delay must be positive");
        }
        Instant cutoff = now.minus(escalationDelay);
        List<MachineAlert> overdue = new ArrayList<>(alerts.findAllByStatusIn(ACTIVE_STATUSES));
        for (MachineAlert alert : overdue) {
            if (alert.getEscalatedAt() == null && !alert.getFirstSeenAt().isAfter(cutoff)) {
                alert.escalate(now);
                saveAndPublish(alert);
            }
        }
    }

    private MachineAlert requireActiveAlert(Long alertId, String tenantId) {
        MachineAlert alert = requireAlert(alertId, tenantId);
        if (!alert.isActive()) {
            throw new InvalidOperationException("Resolved alert must be reopened before it can be changed");
        }
        return alert;
    }

    private MachineAlert requireAlert(Long alertId, String tenantId) {
        return alerts.findByIdAndTenantIdForUpdate(alertId, requireTenantId(tenantId))
            .orElseThrow(() -> new ResourceNotFoundException("Machine alert not found"));
    }

    private MachineAlert saveAndPublish(@NonNull MachineAlert alert) {
        MachineAlert saved = alerts.save(alert);
        publish(saved);
        return saved;
    }

    private void publish(MachineAlert alert) {
        MachineAlertChangedEvent event = new MachineAlertChangedEvent(alert.getId(),
            alert.getTenantId(), alert.getMachineId(), alert.getLocationId(),
            alert.getType().name(), alert.getSeverity().name(), alert.getStatus().name(), Instant.now());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Alert events must be published within a transaction");
        }
        if ("CRITICAL".equals(alert.getSeverity().name()) || "ESCALATED".equals(alert.getStatus().name())) {
            enqueueHandoff("notification", notificationUrl, alert, event);
            enqueueHandoff("maintenance", maintenanceUrl, alert, event);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                events.publishEvent(event);
            }
        });
    }

    private void enqueueHandoff(String target, String url, MachineAlert alert,
                                MachineAlertChangedEvent event) {
        if (url != null && !url.isBlank()) {
            handoffOutbox.save(new AlertHandoffOutbox(target, event.getEventId(), alert.getId(),
                alert.getTenantId(), alert.getMachineId(), alert.getLocationId(), alert.getType().name(),
                alert.getSeverity().name(), alert.getStatus().name(), event.getOccurredAt()));
        }
    }

    private String requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new InvalidOperationException("tenantId is required to access alerts");
        }
        return tenantId.trim();
    }

    private String requireActorId(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidOperationException("actorId is required");
        }
        return actorId.trim();
    }
}
