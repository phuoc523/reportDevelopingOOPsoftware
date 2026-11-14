package com.scentstation.order.security;

import com.scentstation.order.exception.IdentifierDataIntegrityException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class OrderIdentifiers {
    private OrderIdentifiers() {
    }

    public static long request(String value, String field) {
        Long parsed = parseCanonical(value);
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " must be a canonical positive numeric ID");
        }
        return parsed;
    }

    public static String requestText(String value, String field) {
        return Long.toString(request(value, field));
    }

    public static String canonical(Long value, String field) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    field + " must be a positive numeric ID");
        }
        return value.toString();
    }

    public static long stored(String value, String field) {
        Long parsed = parseCanonical(value);
        if (parsed == null) {
            throw new IdentifierDataIntegrityException(
                    "Stored " + field + " is not a canonical positive numeric ID");
        }
        return parsed;
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
