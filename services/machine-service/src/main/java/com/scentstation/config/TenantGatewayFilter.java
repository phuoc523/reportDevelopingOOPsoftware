package com.scentstation.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class TenantGatewayFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    public TenantGatewayFilter(@Value("${machine.gateway.enforce-tenant-header:false}") boolean enabled,
                               ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || !request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        String tenantId = request.getHeader("X-Tenant-Id");
        String queryTenantId = request.getParameter("tenantId");
        boolean isBodyRequest = !"GET".equalsIgnoreCase(request.getMethod())
            && !"DELETE".equalsIgnoreCase(request.getMethod()) && !"HEAD".equalsIgnoreCase(request.getMethod());
        if ((isBodyRequest || queryTenantId != null) && (tenantId == null || tenantId.isBlank())) {
            reject(response, "X-Tenant-Id is required");
            return;
        }
        if (tenantId != null) tenantId = tenantId.trim();
        if (queryTenantId != null && !tenantId.equals(queryTenantId.trim())) {
            reject(response, "X-Tenant-Id does not match the requested tenant");
            return;
        }

        ReplayableRequest replayableRequest = request instanceof ReplayableRequest
            ? (ReplayableRequest) request : new ReplayableRequest(request);
        if (isBodyRequest) {
            String body = new String(replayableRequest.cachedBody, StandardCharsets.UTF_8);
            try {
                JsonNode payload = objectMapper.readTree(body);
                JsonNode bodyTenantId = payload.get("tenantId");
                if (bodyTenantId != null && !bodyTenantId.isNull()
                        && !tenantId.equals(bodyTenantId.asText().trim())) {
                    reject(response, "X-Tenant-Id does not match tenantId in the request body");
                    return;
                }
            } catch (IOException ignored) {
                // Controller remains responsible for malformed JSON validation.
            }
        }
        chain.doFilter(replayableRequest, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message.replace("\"", "\\\"") + "\"}");
    }

    private static final class ReplayableRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        private ReplayableRequest(HttpServletRequest request) throws IOException {
            super(request);
            cachedBody = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public int read() { return stream.read(); }
                @Override
                public boolean isFinished() { return stream.available() == 0; }
                @Override
                public boolean isReady() { return true; }
                @Override
                public void setReadListener(ReadListener readListener) { }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
