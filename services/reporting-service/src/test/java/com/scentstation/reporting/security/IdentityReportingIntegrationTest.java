package com.scentstation.reporting.security;

import com.scentstation.reporting.client.IdentityClient;
import com.scentstation.reporting.config.ReportingSecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real Identity registration, JWT validation and revocation; production Reporting security chain. No fake JWTs/grants. */
@SpringJUnitWebConfig(IdentityReportingIntegrationTest.Config.class)
@EnabledIfEnvironmentVariable(named = "IDENTITY_SERVICE_URL", matches = ".+")
class IdentityReportingIntegrationTest {
    @Configuration
    @EnableWebMvc
    @Import({ReportingSecurityConfig.class, Probe.class})
    static class Config {
        @Bean IdentityClient identityClient() {
            return new IdentityClient(RestClient.builder(), System.getenv("IDENTITY_SERVICE_URL"));
        }
    }
    @RestController
    static class Probe {
        @GetMapping("/api/reporting/transactions/summary") String summary() { return "must-not-reach"; }
    }
    @Autowired IdentityClient identity;
    @Autowired WebApplicationContext context;

    @Test
    void realCustomerJwtIsVerifiedDeniedElevatedAccessAndRejectedAfterLogout() throws Exception {
        var factory = new JdkClientHttpRequestFactory(); factory.setReadTimeout(Duration.ofSeconds(15));
        var api = RestClient.builder().baseUrl(System.getenv("IDENTITY_SERVICE_URL")).requestFactory(factory).build();
        String suffix = UUID.randomUUID().toString();
        var registered = api.post().uri("/api/auth/register").body(Map.of("username", "phase5-" + suffix,
                "email", "phase5-" + suffix + "@example.test", "password", UUID.randomUUID().toString(),
                "fullName", "Phase 5 integration test")).retrieve().body(Map.class);
        assertNotNull(registered);
        String token = (String) registered.get("token");
        assertNotNull(token);
        var actor = identity.verify(token);
        assertTrue(actor.roles().contains("CUSTOMER"));
        assertFalse(ReportingPermissions.has(actor, ReportingPermissions.PLATFORM));

        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/reporting/transactions/summary").header("Authorization", "Bearer " + token)
                        .header("X-Verified-Roles", "PLATFORM_SUPER_ADMIN"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reporting/transactions/summary").header("Authorization", "Bearer invalid.token.signature"))
                .andExpect(status().isUnauthorized());

        api.post().uri("/api/auth/logout").headers(headers -> headers.setBearerAuth(token)).retrieve().toBodilessEntity();
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> identity.verify(token)).getStatusCode());
        mvc.perform(get("/api/reporting/transactions/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
