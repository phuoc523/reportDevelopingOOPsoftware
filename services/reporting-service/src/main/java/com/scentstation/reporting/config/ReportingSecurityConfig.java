package com.scentstation.reporting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.scentstation.reporting.client.IdentityClient;
import com.scentstation.reporting.security.IdentityAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class ReportingSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, IdentityClient identity) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> response.sendError(401)))
                .addFilterBefore(new IdentityAuthenticationFilter(identity), AnonymousAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reporting/health").permitAll()
                        .requestMatchers("/api/reporting/internal/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reporting/dashboard").hasAuthority("REPORT_READ")
                        .requestMatchers(HttpMethod.GET, "/api/reporting/export/**").access((authentication, context) ->
                                new AuthorizationDecision(authentication.get().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("REPORT_EXPORT"))
                                        && authentication.get().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("PLATFORM_REPORT_READ"))))
                        .requestMatchers(HttpMethod.GET, "/api/reporting/**").hasAuthority("PLATFORM_REPORT_READ")
                        .requestMatchers(HttpMethod.POST, "/api/reporting/transactions").hasAuthority("PLATFORM_REPORT_READ")
                        .anyRequest().denyAll());
        return http.build();
    }
}
