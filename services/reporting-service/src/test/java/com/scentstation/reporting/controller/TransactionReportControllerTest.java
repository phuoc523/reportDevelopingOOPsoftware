
package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.TransactionSummaryResponse;
import com.scentstation.reporting.enumtype.ReportPeriod;
import com.scentstation.reporting.service.ReportDateRangeValidator;
import com.scentstation.reporting.service.TransactionReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionReportController.class)
@Import(ReportDateRangeValidator.class)
class TransactionReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionReportService transactionReportService;

    private TransactionSummaryResponse sampleResponse(
            String period,
            LocalDate startDate,
            LocalDate endDate) {

        return new TransactionSummaryResponse(
                100,
                75,
                5,
                8,
                7,
                5,
                new BigDecimal("1500000.00"),
                period,
                startDate,
                endDate
        );
    }

    @Test
    void getSummaryForDayShouldReturnOk() throws Exception {
        LocalDate today = LocalDate.now();

        when(transactionReportService.getSummary(
                eq(ReportPeriod.DAY), any(), any()))
                .thenReturn(sampleResponse("day", today, today));

        mockMvc.perform(get("/api/reporting/transactions/summary")
                        .param("period", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("day"))
                .andExpect(jsonPath("$.startDate").value(today.toString()))
                .andExpect(jsonPath("$.endDate").value(today.toString()));
    }

    @Test
    void getSummaryForWeekShouldReturnOk() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        LocalDate end = start.plusDays(6);

        when(transactionReportService.getSummary(
                eq(ReportPeriod.WEEK), any(), any()))
                .thenReturn(sampleResponse("week", start, end));

        mockMvc.perform(get("/api/reporting/transactions/summary")
                        .param("period", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("week"))
                .andExpect(jsonPath("$.startDate").value(start.toString()))
                .andExpect(jsonPath("$.endDate").value(end.toString()));
    }

    @Test
    void getSummaryForMonthShouldReturnOk() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = today.withDayOfMonth(today.lengthOfMonth());

        when(transactionReportService.getSummary(
                eq(ReportPeriod.MONTH), any(), any()))
                .thenReturn(sampleResponse("month", start, end));

        mockMvc.perform(get("/api/reporting/transactions/summary")
                        .param("period", "month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("month"))
                .andExpect(jsonPath("$.startDate").value(start.toString()))
                .andExpect(jsonPath("$.endDate").value(end.toString()));
    }

    @Test
    void getSummaryForCustomPeriodShouldReturnOk() throws Exception {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 15);

        when(transactionReportService.getSummary(
                eq(ReportPeriod.CUSTOM), eq(start), eq(end)))
                .thenReturn(sampleResponse("custom", start, end));

        mockMvc.perform(get("/api/reporting/transactions/summary")
                        .param("period", "custom")
                        .param("startDate", start.toString())
                        .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("custom"))
                .andExpect(jsonPath("$.startDate").value(start.toString()))
                .andExpect(jsonPath("$.endDate").value(end.toString()));
    }

    @Test
    void invalidPeriodShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/reporting/transactions/summary")
                        .param("period", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customPeriodWithoutDatesShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/reporting/transactions/summary")
                        .param("period", "custom"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customPeriodWithReversedDatesShouldReturnBadRequest()
            throws Exception {

        mockMvc.perform(get("/api/reporting/transactions/summary")
                        .param("period", "custom")
                        .param("startDate", "2026-10-15")
                        .param("endDate", "2026-10-01"))
                .andExpect(status().isBadRequest());
    }
}

