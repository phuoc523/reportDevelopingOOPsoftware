package com.scentstation.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.identity.auth.TokenService;
import com.scentstation.identity.auth.SessionService;
import com.scentstation.identity.model.AuthSession;
import com.scentstation.identity.model.StaffUser;
import com.scentstation.identity.model.Tenant;
import com.scentstation.identity.repository.AuthSessionRepository;
import com.scentstation.identity.repository.LoginAttemptRepository;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired StaffUserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired AuthSessionRepository sessions;
    @Autowired LoginAttemptRepository attempts;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired SessionService sessionService;

    @BeforeEach void init() {
        sessions.deleteAll(); attempts.deleteAll(); users.deleteAll(); tenants.deleteAll();
        Tenant tenant = tenants.save(new Tenant("TEST"));
        users.save(new StaffUser("staff", passwords.encode("correct-horse-123"), tenant, Set.of("BRAND_ADMIN")));
    }

    String login(String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginBody("staff", password))))
            .andReturn().getResponse().getContentAsString();
    }
    record LoginBody(String username, String password) {}

    @Test void loginAndMeAndLogout() throws Exception {
        String token = mapper.readTree(login("correct-horse-123")).get("accessToken").asText();
        assertThat(token).hasSize(43);
        assertThat(sessions.findByTokenHash(tokens.hash(token)))
            .as("Token must exist in database")
            .isPresent();
        assertThat(sessionService.resolve(token))
            .as("SessionService must resolve valid token")
            .isNotNull();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("staff"))
            .andExpect(jsonPath("$.tenantId").isNotEmpty())
            .andExpect(jsonPath("$.roles[0]").value("BRAND_ADMIN"));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
        assertThat(attempts.findByAttemptedUsernameOrderByAttemptedAtDesc("staff").get(0).getResult()).isEqualTo("SUCCESS");
    }

    @Test void invalidPasswordIsDeniedAndAudited() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginBody("staff", "wrong-password"))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertThat(attempts.findByAttemptedUsernameOrderByAttemptedAtDesc("staff")).hasSize(1);
        assertThat(users.findByUsername("staff").orElseThrow().getLastFailedLoginAt()).isNotNull();
    }

    @Test void locksAfterFiveFailures() throws Exception {
        for (int i = 0; i < 4; i++) login("wrong-password");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new LoginBody("staff", "wrong-password"))))
            .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new LoginBody("staff", "correct-horse-123"))))
            .andExpect(status().isLocked());
    }

    @Test void expiredAndDisabledAndTenantSuspendedDenied() throws Exception {
        String token = mapper.readTree(login("correct-horse-123")).get("accessToken").asText();
        AuthSession session = sessions.findByTokenHash(tokens.hash(token)).orElseThrow();
        session.setExpiresAt(Instant.now().minusSeconds(1)); sessions.save(session);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
        String other = mapper.readTree(login("correct-horse-123")).get("accessToken").asText();
        StaffUser user = users.findByUsername("staff").orElseThrow();
        user.setEnabled(false); users.save(user);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + other))
            .andExpect(status().isUnauthorized());
        user.setEnabled(true); users.save(user);
        Tenant tenant = user.getTenant(); tenant.setActive(false); tenants.save(tenant);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + other))
            .andExpect(status().isUnauthorized());
    }

    @Test void noAnonymousStaffSession() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    }
}
