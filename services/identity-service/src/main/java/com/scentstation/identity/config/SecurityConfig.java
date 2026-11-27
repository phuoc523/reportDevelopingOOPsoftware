package com.scentstation.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/register-staff", "/api/auth/register-staff-account", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/health", "/api/auth/tenants").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/change-password", "/api/auth/change-username", "/api/auth/staff-role-request").authenticated()
                .requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/internal/notifications/authorize-service").permitAll()
                .requestMatchers(HttpMethod.POST, "/internal/notifications/resolve-recipients").permitAll()
                .requestMatchers(HttpMethod.POST, "/internal/notifications/authorize-user").authenticated()
                .requestMatchers(HttpMethod.POST, "/internal/auth/introspect").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/audit/recent").authenticated()
                .requestMatchers("/api/platform/**").hasRole("PLATFORM_SUPER_ADMIN")
                // Brand controllers enforce fine-grained permissions with @PreAuthorize/service checks.
                .requestMatchers("/api/brand/**").authenticated()
                // Legacy ADMIN/STAFF values are retained for old records only.
                // They must not authorize privileged endpoints implicitly.
                .requestMatchers("/api/admin/**").hasAnyRole("PLATFORM_SUPER_ADMIN", "BRAND_ADMIN")
                .requestMatchers("/api/staff/**").hasAnyRole("PLATFORM_SUPER_ADMIN", "BRAND_ADMIN",
                    "OPERATIONS_MANAGER", "TECHNICIAN", "INVENTORY_STAFF")
                .requestMatchers("/api/customer/**").hasAnyRole("CUSTOMER", "PLATFORM_SUPER_ADMIN")
                // Fail closed. Add explicit routes and method-level checks for new Identity APIs.
                .anyRequest().denyAll())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
