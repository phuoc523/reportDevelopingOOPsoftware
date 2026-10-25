package com.scentstation.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.identity.auth.AuthDto;
import com.scentstation.identity.auth.SessionService;
import com.scentstation.identity.rbac.RbacPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class BearerAuthenticationFilter extends OncePerRequestFilter {
    private final SessionService sessions;
    private final ObjectMapper mapper;

    public BearerAuthenticationFilter(SessionService sessions, ObjectMapper mapper) {
        this.sessions = sessions;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/v1/") || path.equals("/api/v1/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String bearer = request.getHeader("Authorization");
        if (bearer != null) {
            if (!bearer.startsWith("Bearer ")) {
                unauthorized(response);
                return;
            }
            String token = bearer.substring(7);
            AuthDto.Identity identity = sessions.resolve(token);
            if (identity == null) {
                unauthorized(response);
                return;
            }
            var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
            identity.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            RbacPolicy.permissionsFor(identity.roles()).forEach(permission ->
                    authorities.add(new SimpleGrantedAuthority("PERM_" + permission.name())));
            var auth = new UsernamePasswordAuthenticationToken(identity, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getWriter(), new AuthDto.ErrorResponse("UNAUTHENTICATED", "Authentication required"));
    }
}
