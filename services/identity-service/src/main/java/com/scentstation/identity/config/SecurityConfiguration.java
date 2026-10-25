package com.scentstation.identity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.identity.auth.AuthDto;
import com.scentstation.identity.auth.SessionService;
import com.scentstation.identity.security.BearerAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessionService sessions,
            ObjectMapper mapper) throws Exception {

        BearerAuthenticationFilter filter =
                new BearerAuthenticationFilter(sessions, mapper);

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        (req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.setHeader("Cache-Control", "no-store");

                            mapper.writeValue(
                                    res.getWriter(),
                                    new AuthDto.ErrorResponse(
                                            "UNAUTHENTICATED",
                                            "Authentication required")
                            );
                        }))
                .addFilterBefore(
                        filter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}