package com.scentstation.service;

import com.scentstation.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalServiceKeyVerifier {
    private final String expectedKey;

    public InternalServiceKeyVerifier(
            @Value("${machine.integration.shared-secret:}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    public void verify(String suppliedKey) {
        if (expectedKey.isBlank() || suppliedKey == null || suppliedKey.isBlank()
                || !MessageDigest.isEqual(expectedKey.getBytes(StandardCharsets.UTF_8),
                    suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("A valid internal service key is required");
        }
    }
}
