package com.scentstation.reporting.service;

import com.scentstation.reporting.client.MachineSnapshotClient;
import com.scentstation.reporting.client.OperationInventoryClient;
import com.scentstation.reporting.dto.TransactionFilterRequest;
import com.scentstation.reporting.dto.TransactionResponse;
import com.scentstation.reporting.dto.integration.*;
import com.scentstation.reporting.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportingPerformanceTest {

    @Test
    void operationalAggregationScalesWithinTwoSecondsForLargeDataset() {
        MachineSnapshotClient machines = mock(MachineSnapshotClient.class);
        OperationInventoryClient operations = mock(OperationInventoryClient.class);
        TransactionRepository transactions = mock(TransactionRepository.class);

        int machineCount = 50;
        int commandCount = 10_000;
        int bottleCount = 500;
        int movementCount = 10_000;
        int alertCount = 1_000;
        int ticketCount = 500;

        List<MachineSnapshot> machineList = new ArrayList<>(machineCount);
        for (long i = 1; i <= machineCount; i++) {
            machineList.add(new MachineSnapshot(i, "SN-" + i, "12", "LOC-" + (i % 10), "Kiosk " + i, "ONLINE"));
        }

        Instant baseInstant = Instant.parse("2026-11-19T01:00:00Z");
        List<DispenseCommandSnapshot> commandList = new ArrayList<>(commandCount);
        for (int i = 0; i < commandCount; i++) {
            long mId = (i % machineCount) + 1;
            boolean ok = (i % 10) != 0;
            commandList.add(new DispenseCommandSnapshot("cmd-" + i, "12", mId, "1", "ORD-" + i,
                    (i % 20 == 0) ? "DIAGNOSTIC" : "CUSTOMER",
                    ok ? "DISPENSED" : "FAILED",
                    baseInstant.plusSeconds(i), null, baseInstant.plusSeconds(i + 5), ok));
        }

        List<BottleSnapshot> bottleList = new ArrayList<>(bottleCount);
        for (long i = 1; i <= bottleCount; i++) {
            bottleList.add(new BottleSnapshot(i, 12L, (i % 25) + 1, 1L, "BTL-" + i,
                    new BigDecimal("100"), new BigDecimal(i % 15), new BigDecimal("0.5"), "INSTALLED"));
        }

        LocalDateTime baseTime = LocalDateTime.of(2026, 11, 19, 1, 0);
        List<InventoryMovementSnapshot> movementList = new ArrayList<>(movementCount);
        for (int i = 0; i < movementCount; i++) {
            long bId = (i % bottleCount) + 1;
            long mId = (i % machineCount) + 1;
            String type = i < bottleCount ? "INSTALL" : "CUSTOMER_CONSUMPTION";
            movementList.add(new InventoryMovementSnapshot((long) i + 1, 12L, bId, type, mId, (bId % 4) + 1,
                    new BigDecimal("50"), new BigDecimal("49.5"), new BigDecimal("-0.5"),
                    1L, "perf", "ref-" + i, baseTime.plusSeconds(i)));
        }

        List<MachineAlertSnapshot> alertList = new ArrayList<>(alertCount);
        for (int i = 0; i < alertCount; i++) {
            long mId = (i % machineCount) + 1;
            alertList.add(new MachineAlertSnapshot((long) i + 1, "OFFLINE", "HIGH", "RESOLVED",
                    mId, "LOC-" + (mId % 10), baseInstant, baseInstant.plusSeconds(3600),
                    baseInstant.plusSeconds(300), null, baseInstant.plusSeconds(3600)));
        }

        List<MaintenanceTicketSnapshot> ticketList = new ArrayList<>(ticketCount);
        for (int i = 0; i < ticketCount; i++) {
            long mId = (i % machineCount) + 1;
            ticketList.add(new MaintenanceTicketSnapshot((long) i + 1, 12L, mId, mId % 10, 1L, 1L,
                    "HARDWARE", "MEDIUM", "MEDIUM", i % 2 == 0 ? "OPEN" : "COMPLETED",
                    2L, false, baseTime.plusHours(2), baseTime, null,
                    i % 2 == 0 ? null : baseTime.plusHours(3)));
        }

        List<String> paidOrderIds = new ArrayList<>(5_000);
        for (int i = 0; i < 5_000; i++) paidOrderIds.add("ORD-" + i);

        when(machines.listMachines("12")).thenReturn(machineList);
        when(machines.listAlerts("12")).thenReturn(alertList);
        when(machines.listDispenseCommands("12")).thenReturn(commandList);
        when(operations.listBottles(12L)).thenReturn(bottleList);
        when(operations.listMovements(12L)).thenReturn(movementList);
        when(operations.listMaintenanceTickets(12L)).thenReturn(ticketList);
        when(transactions.findPaidOrderIds(any(), any(), eq("12"), isNull(), isNull())).thenReturn(paidOrderIds);

        var service = new OperationalAnalyticsService(machines, operations, transactions,
                ZoneOffset.UTC, Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")));

        long startNanos = System.nanoTime();
        var result = service.report("12", LocalDate.of(2026, 11, 19),
                LocalDate.of(2026, 11, 19), null, null, null, new BigDecimal("10"));
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertEquals(machineCount, result.machines().total());
        assertEquals(commandCount, result.sprays().total());
        assertEquals(0, result.sprays().paidWithoutSprayConfirmation());
        assertEquals(bottleCount, result.inventory().stockByMachineSlot().size());
        assertTrue(elapsedMs < 2000, "Operational aggregation took too long: " + elapsedMs + "ms");
    }

    @Test
    void csvTransactionExportHandlesTwoThousandRowsWithinOneSecond() {
        TransactionQueryService query = mock(TransactionQueryService.class);
        AdvancedReportService reports = mock(AdvancedReportService.class);
        ExcelExportService exports = new ExcelExportService(query, reports);

        LocalDateTime now = LocalDateTime.of(2026, 11, 19, 10, 0);
        List<TransactionResponse> page1 = new ArrayList<>(1000);
        List<TransactionResponse> page2 = new ArrayList<>(1000);
        for (long i = 1; i <= 1000; i++) {
            page1.add(new TransactionResponse(i, "PAY-" + i, "ORD-" + i, new BigDecimal("25000"),
                    "VND", "PAID", now, now, "12", "1", "1", 10L));
            page2.add(new TransactionResponse(i + 1000, "PAY-" + (i + 1000), "ORD-" + (i + 1000),
                    new BigDecimal("25000"), "VND", "PAID", now, now, "12", "1", "1", 10L));
        }
        when(query.search(any(), any(), eq(false))).thenReturn(
                new PageImpl<>(page1, PageRequest.of(0, 1000), 2000),
                new PageImpl<>(page2, PageRequest.of(1, 1000), 2000));

        long startNanos = System.nanoTime();
        byte[] csv = exports.exportTransactionsCsv(new TransactionFilterRequest(
                null, null, null, null, null, null, null));
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertTrue(csv.length > 50_000);
        assertTrue(elapsedMs < 1000, "CSV export took too long: " + elapsedMs + "ms");
    }
}
