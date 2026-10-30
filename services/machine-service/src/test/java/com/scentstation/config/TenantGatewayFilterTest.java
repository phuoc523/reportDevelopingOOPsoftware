package com.scentstation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantGatewayFilterTest {
    @Test
    void tenantValidationPreservesJsonBodyForController() throws Exception {
        TenantGatewayFilter filter = new TenantGatewayFilter(true, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/locations");
        request.setContentType("application/json");
        String json = "{\"tenantId\":\"tenant-1\",\"name\":\"HQ\"}";
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Tenant-Id", "tenant-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (filteredRequest, filteredResponse) -> {
            String body = new String(filteredRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(json, body);
        };

        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsTenantMismatchInJsonBody() throws Exception {
        TenantGatewayFilter filter = new TenantGatewayFilter(true, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/locations");
        request.setContentType("application/json");
        request.setContent("{\"tenantId\":\"tenant-2\"}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Tenant-Id", "tenant-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) -> {
            throw new AssertionError("Filter chain must not run for a tenant mismatch");
        });

        assertEquals(403, response.getStatus());
    }
}
