package com.scentstation.identity.config;

import com.scentstation.identity.rbac.AccountPrincipal;
import com.scentstation.identity.repository.UserRepository;
import com.scentstation.identity.service.JwtService;
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

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService; this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
            && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String token = header.substring(7);
                String username = jwtService.extractUsername(token); // verifies signature and expiry
                long tokenVersion = jwtService.extractTokenVersion(token);
                if (username != null && !username.isBlank()) {
                    users.findByUsername(username)
                        .filter(u -> u.isActive() && !u.isLocked() && u.getTokenVersion() == tokenVersion)
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
