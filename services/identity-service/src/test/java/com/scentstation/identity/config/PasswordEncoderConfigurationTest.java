package com.scentstation.identity.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncoderConfigurationTest {

    @Test
    void configuredEncoderUsesBcryptAndVerifiesPasswords() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();
        String hash = encoder.encode("test-password");

        assertTrue(hash.matches("^\\$2[aby]\\$.*"));
        assertTrue(encoder.matches("test-password", hash));
        assertFalse(encoder.matches("wrong-password", hash));
    }
}
