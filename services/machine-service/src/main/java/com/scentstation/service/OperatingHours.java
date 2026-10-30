package com.scentstation.service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class OperatingHours {
    private OperatingHours() {
    }

    public static LocalTime currentTime(ZoneId zone) {
        return ZonedDateTime.now(zone).toLocalTime();
    }

    public static boolean includes(LocalTime start, LocalTime end, LocalTime time) {
        if (start == null || end == null) {
            return true;
        }
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }
}
