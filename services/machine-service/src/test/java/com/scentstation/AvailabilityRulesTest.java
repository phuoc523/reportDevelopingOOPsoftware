package com.scentstation;

import com.scentstation.service.OperatingHours;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityRulesTest {
    @Test
    void noScheduleAllowsAllDayAndEqualEndpointsAllowNoTime() {
        assertTrue(OperatingHours.includes(
            null, null, LocalTime.NOON));
        assertFalse(OperatingHours.includes(
            LocalTime.NOON, LocalTime.NOON, LocalTime.NOON));
    }

    @Test
    void regularScheduleIncludesStartButExcludesEnd() {
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(17, 0);

        assertTrue(OperatingHours.includes(start, end, start));
        assertTrue(OperatingHours.includes(
            start, end, LocalTime.of(12, 0)));
        assertFalse(OperatingHours.includes(start, end, end));
    }

    @Test
    void overnightScheduleIncludesBothSidesOfMidnight() {
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(6, 0);

        assertTrue(OperatingHours.includes(
            start, end, LocalTime.of(23, 0)));
        assertTrue(OperatingHours.includes(
            start, end, LocalTime.of(5, 59)));
        assertFalse(OperatingHours.includes(
            start, end, LocalTime.NOON));
    }
}
