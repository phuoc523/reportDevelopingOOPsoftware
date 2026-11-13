package com.scentstation.reporting.service;

import com.scentstation.reporting.client.OperationInventoryClient;
import com.scentstation.reporting.client.UpstreamUnavailableException;
import com.scentstation.reporting.dto.OperationReportResponse;
import com.scentstation.reporting.dto.OperationReportResponse.*;
import com.scentstation.reporting.dto.integration.InventoryMovementSnapshot;
import com.scentstation.reporting.enumtype.ReportPeriod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class OperationReportService {
    private static final Set<String> ACTIVE = Set.of("OPEN", "ASSIGNED", "IN_PROGRESS", "WAITING_FOR_PARTS", "READY_FOR_TEST");
    private final OperationInventoryClient client;
    private final ZoneId sourceZone;
    private final Clock clock;

    @Autowired
    public OperationReportService(OperationInventoryClient client,
            @Value("${integration.operation.zone-id:UTC}") String sourceZone,
            @Value("${reporting.zone-id:Asia/Ho_Chi_Minh}") String reportZone) {
        this(client, ZoneId.of(sourceZone), Clock.system(ZoneId.of(reportZone)));
    }

    OperationReportService(OperationInventoryClient client, ZoneId sourceZone, Clock clock) {
        this.client = client;
        this.sourceZone = sourceZone;
        this.clock = clock;
    }

    public OperationReportResponse getSummary(long tenantId, ReportPeriod period) {
        Map<String, String> upstream = new LinkedHashMap<>();
        upstream.put("operation-failures", "unsupported-missing-list-api");
        BatchSummary batches = null;
        ConsumptionSummary consumption = null;
        MaintenanceSummary maintenance = null;
        try {
            var values = client.listBatches(tenantId);
            batches = new BatchSummary(values.size(), values.stream().mapToLong(value -> value.receivedQuantity()).sum(),
                    values.stream().filter(value -> Boolean.TRUE.equals(value.expired())).count());
            upstream.put("operation-batches", "ok");
        } catch (UpstreamUnavailableException ex) {
            upstream.put("operation-batches", "unavailable");
        }
        try {
            consumption = summarizeConsumption(tenantId, period);
            upstream.put("operation-consumption", "ok");
        } catch (UpstreamUnavailableException ex) {
            upstream.put("operation-consumption", "unavailable");
        }
        try {
            var values = client.listMaintenanceTickets(tenantId);
            Map<String, Long> statuses = new LinkedHashMap<>();
            for (String status : new String[]{"OPEN", "ASSIGNED", "IN_PROGRESS", "WAITING_FOR_PARTS",
                    "READY_FOR_TEST", "COMPLETED", "CANCELLED"}) {
                statuses.put(status, 0L);
            }
            long active = 0, overdue = 0, critical = 0, unknown = 0;
            for (var ticket : values) {
                if (!ACTIVE.contains(ticket.status()) && !Set.of("COMPLETED", "CANCELLED").contains(ticket.status())) { unknown++; }
                statuses.merge(ticket.status(), 1L, Long::sum);
                if (ACTIVE.contains(ticket.status())) {
                    active++;
                    if (Boolean.TRUE.equals(ticket.critical())) { critical++; }
                    if (ticket.dueAt() != null && ticket.dueAt().atZone(sourceZone).toInstant().isBefore(clock.instant())) {
                        overdue++;
                    }
                }
            }
            maintenance = new MaintenanceSummary(values.size(), Map.copyOf(statuses), active, overdue, critical, unknown);
            upstream.put("operation-maintenance", "ok");
        } catch (UpstreamUnavailableException ex) {
            upstream.put("operation-maintenance", "unavailable");
        }
        return new OperationReportResponse(batches, consumption, maintenance, Map.copyOf(upstream));
    }

    private ConsumptionSummary summarizeConsumption(long tenantId, ReportPeriod period) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = switch (period) {
            case DAY -> today;
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> today.withDayOfMonth(1);
            default -> throw new IllegalArgumentException("Operation dashboard supports day, week, month");
        };
        LocalDate end = switch (period) {
            case WEEK -> start.plusDays(6);
            case MONTH -> today.with(TemporalAdjusters.lastDayOfMonth());
            default -> today;
        };
        long customer = 0, diagnostic = 0;
        BigDecimal customerQuantity = BigDecimal.ZERO, diagnosticQuantity = BigDecimal.ZERO;
        Map<String, InventoryMovementSnapshot> commands = new LinkedHashMap<>();
        for (var movement : client.listMovements(tenantId)) {
            if (!Set.of("CUSTOMER_CONSUMPTION", "DIAGNOSTIC_CONSUMPTION").contains(movement.movementType())) { continue; }
            var previous = commands.putIfAbsent(movement.referenceId(), movement);
            if (previous != null) {
                if (!previous.movementType().equals(movement.movementType())
                        || previous.quantityBefore().compareTo(movement.quantityBefore()) != 0
                        || previous.quantityAfter().compareTo(movement.quantityAfter()) != 0
                        || !previous.createdAt().equals(movement.createdAt())) {
                    throw new UpstreamUnavailableException("operation-service",
                            new IllegalArgumentException("Conflicting consumption movements for the same command"));
                }
                continue;
            }
            LocalDate date = movement.createdAt().atZone(sourceZone).withZoneSameInstant(clock.getZone()).toLocalDate();
            if (date.isBefore(start) || date.isAfter(end)) { continue; }
            BigDecimal used = movement.quantityBefore().subtract(movement.quantityAfter());
            if ("CUSTOMER_CONSUMPTION".equals(movement.movementType())) {
                customer++; customerQuantity = customerQuantity.add(used);
            } else {
                diagnostic++; diagnosticQuantity = diagnosticQuantity.add(used);
            }
        }
        return new ConsumptionSummary(start, end, customer, diagnostic, customerQuantity, diagnosticQuantity, null);
    }
}
