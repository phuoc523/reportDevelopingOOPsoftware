package com.scentstation.security;

import com.scentstation.exception.IdentifierDataIntegrityException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class MachineIdentifiers {
    private MachineIdentifiers() {
    }

    public static String actorTenant(Long value) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "A positive tenantId is required");
        }
        return value.toString();
    }

    public static long requestTenant(String value) {
        Long parsed = parseCanonical(value);
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tenantId must be a canonical positive numeric ID");
        }
        return parsed;
    }

    public static long storedTenant(String value) {
        Long parsed = parseCanonical(value);
        if (parsed == null) {
            throw new IdentifierDataIntegrityException(
                    "Stored tenantId is not a canonical positive numeric ID");
        }
        return parsed;
    }

    public static Long exactNumericScope(String value) {
        return parseCanonical(value);
    }

    public static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " must be a positive numeric ID");
        }
    }

    private static Long parseCanonical(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 && value.equals(Long.toString(parsed)) ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
