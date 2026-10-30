package com.scentstation.reporting.service;

import com.scentstation.reporting.enumtype.ReportPeriod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Component
public class ReportDateRangeValidator {

    public void validate(
            ReportPeriod period,
            LocalDate startDate,
            LocalDate endDate) {

        if (period != ReportPeriod.CUSTOM) {
            return;
        }

        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "startDate and endDate are required for custom period."
            );
        }

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "startDate must not be after endDate."
            );
        }
    }
}

