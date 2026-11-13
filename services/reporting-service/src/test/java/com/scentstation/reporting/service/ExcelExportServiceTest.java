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
}
