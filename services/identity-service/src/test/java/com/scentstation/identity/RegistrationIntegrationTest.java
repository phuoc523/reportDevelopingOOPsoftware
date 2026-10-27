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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired StaffUserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired LoginAttemptRepository attempts;
    @Autowired PasswordEncoder passwords;

    @BeforeEach void init() {
        sessions.deleteAll(); attempts.deleteAll(); users.deleteAll();
    }

    private String request(String username, String email, String password) throws Exception {
        return mapper.writeValueAsString(Map.of("username", username,"email",email,"password",password,
                "roles", Set.of("PLATFORM_SUPER_ADMIN"), "tenantId", "forged-tenant"));
    }

    @Test void registrationNeverGrantsRequestedPrivilegedRole() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(request("newcustomer", "CUSTOMER@example.com", "very-strong-secret")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("newcustomer"))
            .andExpect(jsonPath("$.email").value("customer@example.com"));
        StaffUser saved = users.findByUsername("newcustomer").orElseThrow();
        assertThat(saved.getRoles()).containsExactly("CUSTOMER");
        assertThat(saved.getTenant()).isNull();
        assertThat(saved.getPasswordHash()).isNotEqualTo("very-strong-secret");
        assertThat(passwords.matches("very-strong-secret", saved.getPasswordHash())).isTrue();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", "newcustomer", "password", "very-strong-secret"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"));
    }

    @Test void disabledAccountCannotLoginAndCannotUseStaffApis() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(request("customer4", "c4@example.com", "very-strong-secret")))
            .andExpect(status().isCreated());
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", "customer4", "password", "very-strong-secret"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(response).get("accessToken").asText();
        mvc.perform(get("/api/v1/rbac/roles").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        StaffUser account = users.findByUsername("customer4").orElseThrow();
        account.setEnabled(false);
        users.save(account);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", "customer4", "password", "very-strong-secret"))))
            .andExpect(status().isUnauthorized());
    }

    @Test void duplicateAndBadEmailRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(request("client", "customer@example.com", "very-strong-secret")))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(request("client", "other@example.com", "very-strong-secret")))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(request("client2", "customer@example.com", "very-strong-secret")))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(request("client3", "not-an-email", "very-strong-secret")))
            .andExpect(status().isBadRequest());
    }
}
