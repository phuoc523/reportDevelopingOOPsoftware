package com.scentstation.reporting.service;

import com.scentstation.reporting.client.MachineSnapshotClient;
import com.scentstation.reporting.client.OperationInventoryClient;
import com.scentstation.reporting.client.UpstreamUnavailableException;
import com.scentstation.reporting.dto.OperationalReportResponse;
import com.scentstation.reporting.dto.OperationalReportResponse.*;
import com.scentstation.reporting.dto.integration.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.scentstation.reporting.repository.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OperationalAnalyticsService {
    private final MachineSnapshotClient machines;
    private final OperationInventoryClient operations;
    private final ZoneId sourceZone;
    private final Clock clock;
    private final TransactionRepository transactions;

    @Autowired
    public OperationalAnalyticsService(MachineSnapshotClient machines,
            OperationInventoryClient operations,
            TransactionRepository transactions,
            @Value("${integration.operation.zone-id:UTC}") String sourceZone,
            @Value("${reporting.zone-id:Asia/Ho_Chi_Minh}") String reportZone) {
        this(machines, operations, transactions, ZoneId.of(sourceZone), Clock.system(ZoneId.of(reportZone)));
    }

    OperationalAnalyticsService(MachineSnapshotClient machines, OperationInventoryClient operations,
            TransactionRepository transactions, ZoneId sourceZone, Clock clock) {
        this.machines = machines;
        this.operations = operations;
        this.sourceZone = sourceZone;
        this.clock = clock;
        this.transactions = transactions;
    }

    public OperationalReportResponse report(String tenantId, LocalDate start, LocalDate end,
            Long machineId, String locationId, Long productId, BigDecimal refillThreshold) {
        long numericTenant = parseTenant(tenantId);
        if (start == null || end == null || start.isAfter(end)) {
            throw new IllegalArgumentException("A valid startDate/endDate range is required");
        }
        BigDecimal threshold = refillThreshold == null ? new BigDecimal("10") : refillThreshold;
        if (threshold.signum() < 0) throw new IllegalArgumentException("refillThreshold must not be negative");

        Map<String, String> upstream = new LinkedHashMap<>();
        List<MachineSnapshot> machineRows = get(upstream, "machine-service",
                () -> machines.listMachines(tenantId));
        List<MachineAlertSnapshot> alerts = get(upstream, "machine-alerts",
                () -> machines.listAlerts(tenantId));
        List<DispenseCommandSnapshot> commands = get(upstream, "machine-commands",
                () -> machines.listDispenseCommands(tenantId));
        List<BottleSnapshot> bottles = get(upstream, "operation-bottles",
                () -> operations.listBottles(numericTenant));
        List<InventoryMovementSnapshot> movements = get(upstream, "operation-movements",
                () -> operations.listMovements(numericTenant));
        List<MaintenanceTicketSnapshot> tickets = get(upstream, "operation-maintenance",
                () -> operations.listMaintenanceTickets(numericTenant));

        machineRows = machineRows.stream().filter(row -> machineId == null || machineId.equals(row.id()))
                .filter(row -> blank(locationId) || locationId.trim().equals(row.locationId())).toList();
        Set<Long> allowedMachines = machineRows.stream().map(MachineSnapshot::id).collect(Collectors.toSet());
        Map<Long, MachineSnapshot> machineIndex = machineRows.stream()
                .collect(Collectors.toMap(MachineSnapshot::id, Function.identity(), (a, b) -> a));

        alerts = alerts.stream().filter(row -> allowedMachines.contains(row.machineId()))
                .filter(row -> inRange(row.firstSeenAt(), start, end)).toList();
        commands = commands.stream().filter(row -> allowedMachines.contains(row.machineId()))
                .filter(row -> inRange(row.issuedAt(), start, end)).toList();
        tickets = tickets.stream().filter(row -> row.machineId() != null && allowedMachines.contains(row.machineId()))
                .filter(row -> inRange(row.createdAt(), start, end)).toList();

        Map<Long, BottleSnapshot> bottleIndex = bottles.stream()
                .collect(Collectors.toMap(BottleSnapshot::id, Function.identity(), (a, b) -> a));
        if (productId != null) {
            bottles = bottles.stream().filter(row -> productId.equals(row.productId())).toList();
        }
        Set<Long> allowedBottles = bottles.stream().map(BottleSnapshot::id).collect(Collectors.toSet());
        movements = movements.stream().filter(row -> inRange(row.createdAt(), start, end))
                .filter(row -> row.bottleId() == null || allowedBottles.contains(row.bottleId()))
                .filter(row -> machineId == null || machineId.equals(row.machineId())).toList();

        List<String> paidOrders = transactions.findPaidOrderIds(start.atStartOfDay(),
                end.plusDays(1).atStartOfDay(), tenantId,
                machineId == null ? null : machineId.toString(), productId);
        SprayPerformance sprays = sprays(commands, movements, bottleIndex, paidOrders);
        InventoryPerformance inventory = inventory(bottles, movements, threshold);
        IncidentPerformance incidents = incidents(alerts, tickets);
        MachinePerformance machinePerformance = machinePerformance(machineRows, commands, alerts);
        return new OperationalReportResponse(tenantId, start, end, machinePerformance, sprays,
                inventory, incidents, Map.copyOf(upstream));
    }

    private MachinePerformance machinePerformance(List<MachineSnapshot> rows,
            List<DispenseCommandSnapshot> commands, List<MachineAlertSnapshot> alerts) {
        Map<String, Long> statuses = count(rows, row -> normalize(row.operationalStatus()));
        List<MachineMetric> details = rows.stream().map(machine -> {
            long success = commands.stream().filter(c -> machine.id().equals(c.machineId()) && successful(c)).count();
            long failed = commands.stream().filter(c -> machine.id().equals(c.machineId()) && failed(c)).count();
            long unknown = commands.stream().filter(c -> machine.id().equals(c.machineId()) && !successful(c) && !failed(c)).count();
            long alertCount = alerts.stream().filter(a -> machine.id().equals(a.machineId())).count();
            return new MachineMetric(machine.id(), machine.locationId(), normalize(machine.operationalStatus()),
                    success, failed, unknown, rate(success, success + failed + unknown), alertCount);
        }).toList();
        return new MachinePerformance(rows.size(), statuses, details);
    }

    private SprayPerformance sprays(List<DispenseCommandSnapshot> commands,
            List<InventoryMovementSnapshot> movements, Map<Long, BottleSnapshot> bottles,
            List<String> paidOrders) {
        long success = commands.stream().filter(this::successful).count();
        long failed = commands.stream().filter(this::failed).count();
        long unknown = commands.size() - success - failed;
        long diagnostic = commands.stream().filter(this::successful)
                .filter(c -> "DIAGNOSTIC".equals(normalize(c.commandType()))).count();
        long customer = commands.stream().filter(this::successful)
                .filter(c -> !"DIAGNOSTIC".equals(normalize(c.commandType()))).count();
        Map<Long, Long> byMachine = commands.stream().filter(this::successful)
                .collect(Collectors.groupingBy(DispenseCommandSnapshot::machineId, LinkedHashMap::new, Collectors.counting()));
        Map<Long, Long> failedByMachine = commands.stream().filter(this::failed)
                .collect(Collectors.groupingBy(DispenseCommandSnapshot::machineId, LinkedHashMap::new, Collectors.counting()));
        Map<Long, Long> byProduct = new LinkedHashMap<>();
        for (InventoryMovementSnapshot movement : movements) {
            if (!"CUSTOMER_CONSUMPTION".equals(movement.movementType())) continue;
            BottleSnapshot bottle = bottles.get(movement.bottleId());
            if (bottle != null) byProduct.merge(bottle.productId(), 1L, Long::sum);
        }
        Long mostSelected = byProduct.entrySet().stream()
                .max(Map.Entry.<Long, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).orElse(null);
        Set<String> confirmedOrders = commands.stream()
                .filter(command -> successful(command) || failed(command))
                .map(DispenseCommandSnapshot::orderId).filter(orderId -> !blank(orderId))
                .collect(Collectors.toSet());
        long paidWithoutConfirmation = paidOrders.stream().distinct()
                .filter(orderId -> !confirmedOrders.contains(orderId)).count();
        return new SprayPerformance(commands.size(), success, failed, unknown, customer, diagnostic,
                rate(success, commands.size()), mostSelected, Map.copyOf(byProduct),
                Map.copyOf(byMachine), Map.copyOf(failedByMachine), paidWithoutConfirmation);
    }

    private InventoryPerformance inventory(List<BottleSnapshot> bottles,
            List<InventoryMovementSnapshot> movements, BigDecimal threshold) {
        BigDecimal total = bottles.stream().map(BottleSnapshot::currentQuantity)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Long> statuses = count(bottles, row -> normalize(row.status()));
        Map<Long, BigDecimal> remaining = new LinkedHashMap<>();
        for (BottleSnapshot bottle : bottles) remaining.merge(bottle.productId(),
                bottle.currentQuantity(), BigDecimal::add);
        Map<Long, BigDecimal> consumed = new LinkedHashMap<>();
        Map<Long, InventoryMovementSnapshot> latestPlacement = new HashMap<>();
        long refills = 0, replacements = 0, adjustments = 0;
        Map<Long, BottleSnapshot> index = bottles.stream()
                .collect(Collectors.toMap(BottleSnapshot::id, Function.identity(), (a, b) -> a));
        for (InventoryMovementSnapshot movement : movements) {
            switch (normalize(movement.movementType())) {
                case "REFILL" -> refills++;
                case "REPLACE" -> replacements++;
                case "ADJUSTMENT" -> adjustments++;
                default -> { }
            }
            if (Set.of("INSTALL", "REPLACE").contains(normalize(movement.movementType()))) {
                latestPlacement.merge(movement.bottleId(), movement,
                        (a, b) -> a.createdAt().isAfter(b.createdAt()) ? a : b);
            }
            if (Set.of("CUSTOMER_CONSUMPTION", "DIAGNOSTIC_CONSUMPTION").contains(normalize(movement.movementType()))) {
                BottleSnapshot bottle = index.get(movement.bottleId());
                if (bottle != null && movement.quantityBefore() != null && movement.quantityAfter() != null) {
                    consumed.merge(bottle.productId(), movement.quantityBefore().subtract(movement.quantityAfter()), BigDecimal::add);
                }
            }
        }
        List<RefillCandidate> candidates = bottles.stream()
                .filter(b -> b.currentQuantity().compareTo(threshold) <= 0)
                .map(b -> {
                    InventoryMovementSnapshot placement = latestPlacement.get(b.id());
                    return new RefillCandidate(b.id(), b.productId(), placement == null ? null : placement.machineId(),
                            placement == null ? null : placement.slotId(), b.currentQuantity(), threshold);
                }).toList();
        return new InventoryPerformance(total, statuses, Map.copyOf(remaining), Map.copyOf(consumed),
                refills, replacements, adjustments, candidates);
    }

    private IncidentPerformance incidents(List<MachineAlertSnapshot> alerts,
            List<MaintenanceTicketSnapshot> tickets) {
        Map<String, Long> byType = count(alerts, row -> normalize(row.type()));
        Map<String, Long> bySeverity = count(alerts, row -> normalize(row.severity()));
        Map<String, Long> byStatus = count(alerts, row -> normalize(row.status()));
        Map<String, Long> maintenance = count(tickets, row -> normalize(row.status()));
        double acknowledge = averageHours(alerts.stream()
                .filter(a -> a.firstSeenAt() != null && a.acknowledgedAt() != null)
                .map(a -> Duration.between(a.firstSeenAt(), a.acknowledgedAt())).toList());
        double resolution = averageHours(alerts.stream()
                .filter(a -> a.firstSeenAt() != null && a.resolvedAt() != null)
                .map(a -> Duration.between(a.firstSeenAt(), a.resolvedAt())).toList());
        List<Duration> ticketDurations = tickets.stream().filter(t -> t.createdAt() != null && t.closedAt() != null)
                .map(t -> Duration.between(t.createdAt(), t.closedAt())).toList();
        double maintenanceResolution = averageHours(ticketDurations);
        double downtime = ticketDurations.stream().mapToDouble(d -> Math.max(0, d.toMinutes()) / 60.0).sum();
        Map<Long, Long> failures = alerts.stream().filter(a -> a.machineId() != null)
                .collect(Collectors.groupingBy(MachineAlertSnapshot::machineId, Collectors.counting()));
        List<Long> highFailure = failures.entrySet().stream().filter(e -> e.getValue() >= 3)
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
        return new IncidentPerformance(byType, bySeverity, byStatus, maintenance, acknowledge,
                resolution, maintenanceResolution, round(downtime), highFailure);
    }

    private <T> List<T> get(Map<String, String> upstream, String key, Source<T> source) {
        try {
            List<T> values = source.get(); upstream.put(key, "ok"); return values;
        } catch (UpstreamUnavailableException ex) {
            upstream.put(key, "unavailable"); return List.of();
        }
    }

    private long parseTenant(String tenantId) {
        try {
            long value = Long.parseLong(tenantId);
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("tenantId must be a positive number");
        }
    }

    private boolean successful(DispenseCommandSnapshot value) {
        return "DISPENSED".equals(normalize(value.status())) && Boolean.TRUE.equals(value.resultSuccess());
    }
    private boolean failed(DispenseCommandSnapshot value) {
        return "FAILED".equals(normalize(value.status())) || Boolean.FALSE.equals(value.resultSuccess());
    }
    private boolean inRange(Instant instant, LocalDate start, LocalDate end) {
        if (instant == null) return false;
        LocalDate date = instant.atZone(clock.getZone()).toLocalDate();
        return !date.isBefore(start) && !date.isAfter(end);
    }
    private boolean inRange(LocalDateTime value, LocalDate start, LocalDate end) {
        if (value == null) return false;
        LocalDate date = value.atZone(sourceZone).withZoneSameInstant(clock.getZone()).toLocalDate();
        return !date.isBefore(start) && !date.isAfter(end);
    }
    private <T> Map<String, Long> count(List<T> values, Function<T, String> classifier) {
        return values.stream().collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.counting()));
    }
    private double rate(long value, long total) { return total == 0 ? 0 : round(value * 100.0 / total); }
    private double averageHours(List<Duration> values) {
        return values.isEmpty() ? 0 : round(values.stream().mapToLong(Duration::toMinutes).average().orElse(0) / 60.0);
    }
    private double round(double value) { return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue(); }
    private String normalize(String value) { return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    @FunctionalInterface private interface Source<T> { List<T> get(); }
}
