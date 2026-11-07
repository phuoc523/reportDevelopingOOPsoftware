package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionFilterRequest;
import com.scentstation.reporting.dto.TransactionResponse;
import com.scentstation.reporting.dto.RevenueDetailResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelExportService {
    private final TransactionQueryService queryService;
    private final AdvancedReportService reportService;

    public ExcelExportService(TransactionQueryService queryService,
                              AdvancedReportService reportService) {
        this.queryService = queryService;
        this.reportService = reportService;
    }

    public byte[] exportTransactions(TransactionFilterRequest filter) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Transactions");
            String[] headers = {"ID", "Reference", "Order ID", "Amount", "Currency",
                    "Status", "Created At", "Paid At"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            int rowIndex = 1;
            int page = 0;
            Page<TransactionResponse> result;
            do {
                result = queryService.search(filter,
                        PageRequest.of(page++, 1000, Sort.by(Sort.Direction.DESC, "createdAt")), false);
                for (TransactionResponse t : result.getContent()) {
                    Row row = sheet.createRow(rowIndex++);
                    cell(row, 0, t.id() == null ? "" : t.id().toString());
                    cell(row, 1, t.reference());
                    cell(row, 2, t.orderId());
                    if (t.amount() != null) row.createCell(3).setCellValue(t.amount().doubleValue());
                    cell(row, 4, t.currency());
                    cell(row, 5, t.status());
                    cell(row, 6, t.createdAt() == null ? "" : t.createdAt().toString());
                    cell(row, 7, t.paidAt() == null ? "" : t.paidAt().toString());
                }
            } while (result.hasNext());

            style(sheet, headers.length);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate transaction Excel file", e);
        }
    }

    public byte[] exportRevenue(java.time.LocalDate start, java.time.LocalDate end) {
        List<RevenueDetailResponse> rows = reportService.revenueDetails(start, end);
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Revenue");
            String[] headers = {"Date", "Successful Transactions", "Revenue"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            int index = 1;
            for (RevenueDetailResponse item : rows) {
                Row row = sheet.createRow(index++);
                cell(row, 0, item.date().toString());
                row.createCell(1).setCellValue(item.transactionCount());
                row.createCell(2).setCellValue(item.revenue().doubleValue());
            }
            style(sheet, headers.length);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate revenue Excel file", e);
        }
    }

    private void cell(Row row, int index, String value) {
        row.createCell(index).setCellValue(value == null ? "" : value);
    }

    private void style(Sheet sheet, int columns) {
        sheet.createFreezePane(0, 1);
        for (int i = 0; i < columns; i++) sheet.autoSizeColumn(i);
    }
}

