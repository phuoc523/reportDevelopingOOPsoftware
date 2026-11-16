package com.scentstation.catalog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalServiceKeyVerifier {
    private final String expected;

    public InternalServiceKeyVerifier(@Value("${security.internal.service-key:}") String expected) {
        this.expected = expected;
    }

    public void verify(String supplied) {
        if (expected.isBlank() || supplied == null || supplied.isBlank()
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid internal service key is required");
        }
    }
}
