package com.scentstation.identity.config;

import com.scentstation.identity.rbac.AccountPrincipal;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.UserRepository;
import com.scentstation.identity.service.JwtService;
import com.scentstation.identity.tenant.Tenant;
import com.scentstation.identity.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository users;
    private final TenantRepository tenants;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users, TenantRepository tenants) {
        this.jwtService = jwtService; this.users = users; this.tenants = tenants;
    }

    private boolean permittedTenant(User user) {
        // Public kiosk customers may have no tenant. Assigned staff must not
        // keep using JWTs issued before their tenant was suspended.
        if (user.getTenantId() == null) return true;
        return tenants.findStatusById(user.getTenantId()).orElse(null) == Tenant.Status.ACTIVE;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
            && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String token = header.substring(7);
                var session = jwtService.readSession(token);
                String username = session.username();
                if (session.userId() > 0 && session.tokenVersion() >= 0 && username != null && !username.isBlank()) {
                    users.findByUsername(username)
                        .filter(u -> Long.valueOf(session.userId()).equals(u.getId())
                            && u.isActive() && !u.isLocked() && u.getTokenVersion() == session.tokenVersion()
                            && permittedTenant(u))
                        .ifPresent(u -> {
                            AccountPrincipal actor = AccountPrincipal.from(u);
                            var authorities = actor.roles().stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
                            var authentication = new UsernamePasswordAuthenticationToken(actor, null, authorities);
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
                }
            } catch (RuntimeException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
