package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.OperationalReportResponse;
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
import java.nio.charset.StandardCharsets;
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
                    "Status", "Created At", "Paid At", "Tenant ID", "Machine ID", "Slot ID", "Product ID"};
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
                    cell(row, 8, t.tenantId());
                    cell(row, 9, t.machineId());
                    cell(row, 10, t.slotId());
                    cell(row, 11, t.productId() == null ? "" : t.productId().toString());
                }
            } while (result.hasNext());

            style(sheet, headers.length);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate transaction Excel file", e);
        }
    }

    public byte[] exportTransactionsCsv(TransactionFilterRequest filter) {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Reference,Order ID,Amount,Currency,Status,Created At,Paid At,Tenant ID,Machine ID,Slot ID,Product ID\n");
        int page = 0;
        Page<TransactionResponse> result;
        do {
            result = queryService.search(filter,
                    PageRequest.of(page++, 1000, Sort.by(Sort.Direction.DESC, "createdAt")), false);
            for (TransactionResponse t : result.getContent()) {
                csv.append(csvCell(t.id() == null ? "" : t.id().toString())).append(',')
                        .append(csvCell(t.reference())).append(',')
                        .append(csvCell(t.orderId())).append(',')
                        .append(csvCell(t.amount() == null ? "" : t.amount().toPlainString())).append(',')
                        .append(csvCell(t.currency())).append(',')
                        .append(csvCell(t.status())).append(',')
                        .append(csvCell(t.createdAt() == null ? "" : t.createdAt().toString())).append(',')
                        .append(csvCell(t.paidAt() == null ? "" : t.paidAt().toString())).append(',')
                        .append(csvCell(t.tenantId())).append(',')
                        .append(csvCell(t.machineId())).append(',')
                        .append(csvCell(t.slotId())).append(',')
                        .append(csvCell(t.productId() == null ? "" : t.productId().toString())).append('\n');
            }
        } while (result.hasNext());
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportRevenue(java.time.LocalDate start, java.time.LocalDate end) {
        return exportRevenue(start, end, null, null, null);
    }

    public byte[] exportRevenue(java.time.LocalDate start, java.time.LocalDate end,
            String tenantId, String machineId, Long productId) {
        List<RevenueDetailResponse> rows = tenantId == null && machineId == null && productId == null
                ? reportService.revenueDetails(start, end)
                : reportService.revenueDetails(start, end, tenantId, machineId, productId);
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

    public byte[] exportRevenueCsv(java.time.LocalDate start, java.time.LocalDate end,
            String tenantId, String machineId, Long productId) {
        List<RevenueDetailResponse> rows = tenantId == null && machineId == null && productId == null
                ? reportService.revenueDetails(start, end)
                : reportService.revenueDetails(start, end, tenantId, machineId, productId);
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Successful Transactions,Revenue\n");
        for (RevenueDetailResponse item : rows) {
            csv.append(csvCell(item.date().toString())).append(',')
                    .append(item.transactionCount()).append(',')
                    .append(csvCell(item.revenue() == null ? "0" : item.revenue().toPlainString())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportOperationsExcel(OperationalReportResponse report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet machinesSheet = workbook.createSheet("Machines");
            String[] machineHeaders = {"Machine ID", "Location ID", "Status",
                    "Successful Sprays", "Failed Sprays", "Unconfirmed Sprays", "Success Rate (%)", "Alerts"};
            Row mh = machinesSheet.createRow(0);
            for (int i = 0; i < machineHeaders.length; i++) mh.createCell(i).setCellValue(machineHeaders[i]);
            int mRow = 1;
            if (report.machines() != null && report.machines().details() != null) {
                for (var item : report.machines().details()) {
                    Row row = machinesSheet.createRow(mRow++);
                    cell(row, 0, item.machineId() == null ? "" : item.machineId().toString());
                    cell(row, 1, item.locationId());
                    cell(row, 2, item.status());
                    row.createCell(3).setCellValue(item.successfulSprays());
                    row.createCell(4).setCellValue(item.failedSprays());
                    row.createCell(5).setCellValue(item.unconfirmedSprays());
                    row.createCell(6).setCellValue(item.successRate());
                    row.createCell(7).setCellValue(item.alerts());
                }
            }
            style(machinesSheet, machineHeaders.length);

            Sheet stockSheet = workbook.createSheet("Inventory");
            String[] stockHeaders = {"Bottle ID", "Product ID", "Machine ID", "Slot ID", "Current Quantity", "Status"};
            Row sh = stockSheet.createRow(0);
            for (int i = 0; i < stockHeaders.length; i++) sh.createCell(i).setCellValue(stockHeaders[i]);
            int sRow = 1;
            if (report.inventory() != null && report.inventory().stockByMachineSlot() != null) {
                for (var item : report.inventory().stockByMachineSlot()) {
                    Row row = stockSheet.createRow(sRow++);
                    cell(row, 0, item.bottleId() == null ? "" : item.bottleId().toString());
                    cell(row, 1, item.productId() == null ? "" : item.productId().toString());
                    cell(row, 2, item.machineId() == null ? "" : item.machineId().toString());
                    cell(row, 3, item.slotId() == null ? "" : item.slotId().toString());
                    if (item.currentQuantity() != null) row.createCell(4).setCellValue(item.currentQuantity().doubleValue());
                    cell(row, 5, item.status());
                }
            }
            style(stockSheet, stockHeaders.length);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate operations Excel file", e);
        }
    }

    public byte[] exportOperationsCsv(OperationalReportResponse report) {
        StringBuilder csv = new StringBuilder();
        csv.append("Machine ID,Location ID,Status,Successful Sprays,Failed Sprays,Unconfirmed Sprays,Success Rate (%),Alerts\n");
        if (report.machines() != null && report.machines().details() != null) {
            for (var item : report.machines().details()) {
                csv.append(csvCell(item.machineId() == null ? "" : item.machineId().toString())).append(',')
                        .append(csvCell(item.locationId())).append(',')
                        .append(csvCell(item.status())).append(',')
                        .append(item.successfulSprays()).append(',')
                        .append(item.failedSprays()).append(',')
                        .append(item.unconfirmedSprays()).append(',')
                        .append(item.successRate()).append(',')
                        .append(item.alerts()).append('\n');
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void cell(Row row, int index, String value) {
        row.createCell(index).setCellValue(value == null ? "" : value);
    }

    private String csvCell(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private void style(Sheet sheet, int columns) {
        sheet.createFreezePane(0, 1);
        for (int i = 0; i < columns; i++) sheet.autoSizeColumn(i);
    }
}

