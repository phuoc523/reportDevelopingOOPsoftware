package com.scentstation.reporting.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalApiKeyVerifier {

    private final byte[] expectedKey;

    public InternalApiKeyVerifier(@Value("${reporting.sync.api-key}") String syncApiKey) {
        if (syncApiKey == null || syncApiKey.isBlank()) {
            throw new IllegalArgumentException("reporting.sync.api-key must not be blank");
        }
        this.expectedKey = syncApiKey.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String providedKey) {
        if (providedKey == null || providedKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
        }
        byte[] provided = providedKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKey, provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
        }
    }
}
