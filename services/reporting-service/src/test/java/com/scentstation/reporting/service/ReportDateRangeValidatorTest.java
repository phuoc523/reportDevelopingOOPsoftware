
package com.scentstation.reporting.service;

import com.scentstation.reporting.enumtype.ReportPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportDateRangeValidatorTest {

    private ReportDateRangeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ReportDateRangeValidator();
    }

    @Test
    void shouldAcceptValidCustomDateRange() {
        assertDoesNotThrow(() -> validator.validate(
                ReportPeriod.CUSTOM,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 20)
        ));
    }

    @Test
    void shouldRejectCustomPeriodWithoutStartDate() {
        assertThrows(ResponseStatusException.class, () ->
                validator.validate(
                        ReportPeriod.CUSTOM,
                        null,
                        LocalDate.of(2026, 10, 20)
                ));
    }

    @Test
    void shouldRejectCustomPeriodWithoutEndDate() {
        assertThrows(ResponseStatusException.class, () ->
                validator.validate(
                        ReportPeriod.CUSTOM,
                        LocalDate.of(2026, 10, 1),
                        null
                ));
    }

    @Test
    void shouldRejectStartDateAfterEndDate() {
        assertThrows(ResponseStatusException.class, () ->
                validator.validate(
                        ReportPeriod.CUSTOM,
                        LocalDate.of(2026, 10, 20),
                        LocalDate.of(2026, 10, 1)
                ));
    }

    @Test
    void shouldAcceptSameStartAndEndDate() {
        assertDoesNotThrow(() -> validator.validate(
                ReportPeriod.CUSTOM,
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 10)
        ));
    }

    @Test
    void shouldNotRequireCustomDatesForMonthlyReport() {
        assertDoesNotThrow(() ->
                validator.validate(ReportPeriod.MONTH, null, null));
    }
}

