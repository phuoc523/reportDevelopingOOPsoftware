package com.scentstation.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.identity.model.StaffUser;
import com.scentstation.identity.repository.AuthSessionRepository;
import com.scentstation.identity.repository.LoginAttemptRepository;
import com.scentstation.identity.repository.StaffUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"auth.jwt.enabled=true",
        "auth.jwt.secret-base64=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA="})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class JwtAuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired StaffUserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired LoginAttemptRepository attempts;
    @Autowired PasswordEncoder encoder;

    @BeforeEach void init() {
        sessions.deleteAll(); attempts.deleteAll(); users.deleteAll();
        users.save(new StaffUser("jwtuser", encoder.encode("jwt-password123"), null, Set.of("CUSTOMER")));
    }

    @Test void jwtIssuedValidatedAndRevoked() throws Exception {
        String json = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", "jwtuser", "password", "jwt-password123"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(json).get("accessToken").asText();
        assertThat(token.split("\\.")).hasSize(3);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token + "A"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }
}
