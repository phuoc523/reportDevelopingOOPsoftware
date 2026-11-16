package com.scentstation.reporting.security;

import com.scentstation.reporting.client.IdentityClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

public class IdentityAuthenticationFilter extends OncePerRequestFilter {
    private final IdentityClient identity;
    public IdentityAuthenticationFilter(IdentityClient identity) { this.identity = identity; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !path.startsWith("/api/reporting/") || path.equals("/api/reporting/health")
                || path.startsWith("/api/reporting/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ") || header.length() <= 7 || header.length() > 8192) {
                response.sendError(401, "Bearer token required"); return;
            }
            var actor = identity.verify(header.substring(7));
            if (path(request).equals("/api/reporting/dashboard")
                    && !ReportingPermissions.has(actor, ReportingPermissions.PLATFORM)) {
                String[] tenants = request.getParameterValues("tenantId");
                if (actor.tenantId() == null || !actor.locationIds().isEmpty() || !actor.machineIds().isEmpty()
                        || (tenants != null && (tenants.length != 1 || !actor.tenantId().toString().equals(tenants[0].trim())))) {
                    response.sendError(403, "Tenant or assigned scope is not supported by this report"); return;
                }
            }
            var authorities = List.of(ReportingPermissions.READ, ReportingPermissions.EXPORT, ReportingPermissions.PLATFORM)
                    .stream().filter(permission -> ReportingPermissions.has(actor, permission))
                    .map(SimpleGrantedAuthority::new).toList();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, authorities));
            SecurityContextHolder.setContext(context);
        } catch (ResponseStatusException ex) {
            SecurityContextHolder.clearContext();
            response.sendError(ex.getStatusCode().value(), ex.getReason()); return;
        }
        chain.doFilter(request, response);
    }

    private String path(HttpServletRequest request) {
        return org.springframework.web.util.UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }
}
