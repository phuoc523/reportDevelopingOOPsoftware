package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.TransactionFilterRequest;
import com.scentstation.reporting.service.ExcelExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reporting/export")
public class ExcelExportController {
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final ExcelExportService exportService;

    public ExcelExportController(ExcelExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/transactions")
    public ResponseEntity<byte[]> exportTransactions(
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {
        byte[] file = exportService.exportTransactions(new TransactionFilterRequest(
                reference, orderId, status, startDate, endDate, minAmount, maxAmount));
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.xlsx")
                .body(file);
    }

    @GetMapping("/revenue")
    public ResponseEntity<byte[]> exportRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        byte[] file = exportService.exportRevenue(startDate, endDate);
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=revenue-report.xlsx")
                .body(file);
    }
}

