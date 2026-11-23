package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcelExportServiceTest {
    @Mock TransactionQueryService query;
    @Mock AdvancedReportService reports;
    @InjectMocks ExcelExportService exports;

    @Test
    void transactionWorkbookPreservesPagesCurrenciesAndStringReferences() throws Exception {
        var created = LocalDateTime.of(2026, 11, 13, 10, 0);
        var first = new TransactionResponse(1L, "=2+2", "ORDER1", new BigDecimal("25000"), "VND", "PAID", created, created);
        var second = new TransactionResponse(2L, "PAY2", "ORDER2", new BigDecimal("12.50"), "USD", "PENDING", created, null);
        when(query.search(any(), any(), eq(false))).thenReturn(
                new PageImpl<>(List.of(first), PageRequest.of(0, 1), 2),
                new PageImpl<>(List.of(second), PageRequest.of(1, 1), 2));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(exports.exportTransactions(
                new TransactionFilterRequest(null, null, null, null, null, null, null))))) {
            var sheet = workbook.getSheet("Transactions");
            assertNotNull(sheet);
            assertEquals(3, sheet.getPhysicalNumberOfRows());
            assertEquals("Reference", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals(CellType.STRING, sheet.getRow(1).getCell(1).getCellType());
            assertEquals("=2+2", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals(25000, sheet.getRow(1).getCell(3).getNumericCellValue());
            assertEquals("USD", sheet.getRow(2).getCell(4).getStringCellValue());
            assertEquals("", sheet.getRow(2).getCell(7).getStringCellValue());
        }
        verify(query, times(2)).search(any(), any(), eq(false));
    }

    @Test
    void revenueWorkbookCanBeOpenedAndContainsSourceValues() throws Exception {
        var date = LocalDate.of(2026, 11, 13);
        when(reports.revenueDetails(date, date)).thenReturn(List.of(new RevenueDetailResponse(date, 2, new BigDecimal("50000"))));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(exports.exportRevenue(date, date)))) {
            var sheet = workbook.getSheet("Revenue");
            assertEquals(2, sheet.getPhysicalNumberOfRows());
            assertEquals("2026-11-13", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(2, sheet.getRow(1).getCell(1).getNumericCellValue());
            assertEquals(50000, sheet.getRow(1).getCell(2).getNumericCellValue());
        }
    }

    @Test
    void csvAndOperationsExportsContainExpectedHeadersAndRows() throws Exception {
        var created = LocalDateTime.of(2026, 11, 13, 10, 0);
        var txn = new TransactionResponse(1L, "PAY-1", "ORD-1", new BigDecimal("25000"),
                "VND", "PAID", created, created, "12", "44", "3", 91L);
        when(query.search(any(), any(), eq(false))).thenReturn(
                new PageImpl<>(List.of(txn), PageRequest.of(0, 1000), 1));
        String txnCsv = new String(exports.exportTransactionsCsv(
                new TransactionFilterRequest(null, null, null, null, null, null, null)),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(txnCsv.contains("Tenant ID,Machine ID,Slot ID,Product ID"));
        assertTrue(txnCsv.contains("PAY-1,ORD-1,25000,VND,PAID"));
        assertTrue(txnCsv.contains(",12,44,3,91"));

        var date = LocalDate.of(2026, 11, 13);
        when(reports.revenueDetails(date, date)).thenReturn(List.of(new RevenueDetailResponse(date, 2, new BigDecimal("50000"))));
        String revCsv = new String(exports.exportRevenueCsv(date, date, null, null, null),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(revCsv.contains("2026-11-13,2,50000"));

        var opReport = new OperationalReportResponse("12", date, date,
                new OperationalReportResponse.MachinePerformance(1, java.util.Map.of("ONLINE", 1L),
                        List.of(new OperationalReportResponse.MachineMetric(44L, "LOC-A", "ONLINE", 10, 1, 0, 90.91, 0))),
                new OperationalReportResponse.SprayPerformance(11, 10, 1, 0, 10, 0, 90.91, 91L,
                        java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), 0),
                new OperationalReportResponse.InventoryPerformance(new BigDecimal("50"), java.util.Map.of(),
                        java.util.Map.of(), java.util.Map.of(), 0, 0, 0, List.of(),
                        List.of(new OperationalReportResponse.SlotStockItem(50L, 91L, 44L, 3L, new BigDecimal("50"), "INSTALLED")),
                        java.util.Map.of(), List.of(), List.of()),
                new OperationalReportResponse.IncidentPerformance(java.util.Map.of(), java.util.Map.of(),
                        java.util.Map.of(), java.util.Map.of(), 0, 0, 0, 0, List.of()),
                java.util.Map.of());

        String opCsv = new String(exports.exportOperationsCsv(opReport), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(opCsv.contains("44,LOC-A,ONLINE,10,1,0,90.91,0"));

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(exports.exportOperationsExcel(opReport)))) {
            assertNotNull(workbook.getSheet("Machines"));
            assertNotNull(workbook.getSheet("Inventory"));
            assertEquals("44", workbook.getSheet("Machines").getRow(1).getCell(0).getStringCellValue());
            assertEquals("50", workbook.getSheet("Inventory").getRow(1).getCell(0).getStringCellValue());
        }
    }
}
