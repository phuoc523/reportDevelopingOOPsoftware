package com.scentstation.identity.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter) throws Exception {

        String platform = "PLATFORM_SUPER_ADMIN";
        String brandAdmin = "BRAND_ADMIN";
        String operations = "OPERATIONS_MANAGER";
        String technician = "TECHNICIAN";
        String inventory = "INVENTORY_STAFF";
        String viewer = "VIEWER";

        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
<<<<<<< Updated upstream
                .requestMatchers("/api/auth/**", "/error").permitAll()
                .requestMatchers("/api/tenants/**").hasRole(platform)
                .requestMatchers("/api/admin/users/**").hasRole(platform)
                .requestMatchers("/api/admin/staff-users/**")
                    .hasAnyRole(platform, brandAdmin)
                .requestMatchers("/api/admin/**").hasAnyRole(platform, "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/staff/operations/**")
                    .hasAnyRole(platform, brandAdmin, operations, viewer)
                .requestMatchers(HttpMethod.HEAD, "/api/staff/operations/**")
                    .hasAnyRole(platform, brandAdmin, operations, viewer)
                .requestMatchers(HttpMethod.GET, "/api/staff/maintenance/**")
                    .hasAnyRole(platform, brandAdmin, operations, technician, viewer)
                .requestMatchers(HttpMethod.HEAD, "/api/staff/maintenance/**")
                    .hasAnyRole(platform, brandAdmin, operations, technician, viewer)
                .requestMatchers(HttpMethod.GET, "/api/staff/inventory/**")
                    .hasAnyRole(platform, brandAdmin, operations, inventory, viewer)
                .requestMatchers(HttpMethod.HEAD, "/api/staff/inventory/**")
                    .hasAnyRole(platform, brandAdmin, operations, inventory, viewer)
                .requestMatchers(HttpMethod.GET, "/api/staff/reports/**")
                    .hasAnyRole(platform, brandAdmin, operations, viewer)
                .requestMatchers(HttpMethod.HEAD, "/api/staff/reports/**")
                    .hasAnyRole(platform, brandAdmin, operations, viewer)
                .requestMatchers(HttpMethod.DELETE, "/api/staff/operations/**")
                    .hasRole(platform)
                .requestMatchers(HttpMethod.DELETE, "/api/staff/maintenance/**")
                    .hasRole(platform)
                .requestMatchers(HttpMethod.DELETE, "/api/staff/inventory/**")
                    .hasRole(platform)
                .requestMatchers(HttpMethod.POST, "/api/staff/operations/**")
                    .hasAnyRole(platform, brandAdmin, operations)
                .requestMatchers(HttpMethod.PUT, "/api/staff/operations/**")
                    .hasAnyRole(platform, brandAdmin, operations)
                .requestMatchers(HttpMethod.PATCH, "/api/staff/operations/**")
                    .hasAnyRole(platform, brandAdmin, operations)
                .requestMatchers(HttpMethod.POST, "/api/staff/maintenance/**")
                    .hasAnyRole(platform, brandAdmin, operations, technician)
                .requestMatchers(HttpMethod.PUT, "/api/staff/maintenance/**")
                    .hasAnyRole(platform, brandAdmin, operations, technician)
                .requestMatchers(HttpMethod.PATCH, "/api/staff/maintenance/**")
                    .hasAnyRole(platform, brandAdmin, operations, technician)
                .requestMatchers(HttpMethod.POST, "/api/staff/inventory/**")
                    .hasAnyRole(platform, brandAdmin, operations, inventory)
                .requestMatchers(HttpMethod.PUT, "/api/staff/inventory/**")
                    .hasAnyRole(platform, brandAdmin, operations, inventory)
                .requestMatchers(HttpMethod.PATCH, "/api/staff/inventory/**")
                    .hasAnyRole(platform, brandAdmin, operations, inventory)
                .requestMatchers("/api/staff/reports/**").hasRole(platform)
                .requestMatchers("/api/staff/**").hasAnyRole(platform, "ADMIN", "STAFF")
                .requestMatchers("/api/customer/**").hasAnyRole(platform, "ADMIN", "CUSTOMER")
                .anyRequest().authenticated())
=======
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/health").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/internal/auth/introspect").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/audit/recent").authenticated()
                .requestMatchers("/api/platform/**").hasRole("PLATFORM_SUPER_ADMIN")
                .requestMatchers("/api/brand/**").hasRole("BRAND_ADMIN")
                .requestMatchers("/api/admin/**").hasAnyRole("PLATFORM_SUPER_ADMIN", "BRAND_ADMIN")
                .requestMatchers("/api/staff/**").hasAnyRole("PLATFORM_SUPER_ADMIN", "BRAND_ADMIN",
                    "OPERATIONS_MANAGER", "TECHNICIAN", "INVENTORY_STAFF")
                .requestMatchers("/api/customer/**").hasAnyRole("CUSTOMER", "PLATFORM_SUPER_ADMIN")
                .anyRequest().denyAll())
>>>>>>> Stashed changes
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
