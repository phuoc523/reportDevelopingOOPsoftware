$ErrorActionPreference = 'Stop'
$repo = 'E:\XD\NV1\reportDevelopingOOPsoftware'
Set-Location $repo
if ((git branch --show-current) -ne 'feature/identity-platform') { throw 'Hay chuyen sang feature/identity-platform truoc.' }

function Write-Utf8File([string]$Path, [string]$Content) {
  $parent = Split-Path -Parent $Path
  if (!(Test-Path $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
  Set-Content -LiteralPath $Path -Value $Content -Encoding UTF8
}

Write-Utf8File 'services/identity-service/pom.xml' @'
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.3.5</version><relativePath/></parent>
  <groupId>com.scentstation</groupId><artifactId>identity-service</artifactId><version>0.0.1-SNAPSHOT</version>
  <properties><java.version>17</java.version><spring-cloud.version>2023.0.3</spring-cloud.version><jjwt.version>0.12.6</jjwt.version></properties>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>${jjwt.version}</version></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
  <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
</project>
'@

Write-Utf8File 'services/identity-service/src/main/resources/application.yml' @'
server:
  port: 8081
spring:
  application:
    name: identity-service
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/scentstation_identity?createDatabaseIfNotExist=true}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:password}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
security:
  jwt:
    # Replace this development key with a strong secret from an environment variable in real deployments.
    secret: ${JWT_SECRET:U2NlbnRTdGF0aW9uRGV2ZWxvcG1lbnRLZXlGb3JKV1QyMDI2IQ==}
    expiration-ms: ${JWT_EXPIRATION_MS:86400000}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/IdentityServiceApplication.java' @'
package com.scentstation.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IdentityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/entity/Role.java' @'
package com.scentstation.identity.entity;

public enum Role {
    ADMIN, STAFF, CUSTOMER
}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/entity/User.java' @'
package com.scentstation.identity.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String username;
    @Column(nullable = false, unique = true, length = 160)
    private String email;
    @Column(nullable = false)
    private String password;
    @Column(nullable = false, length = 120)
    private String fullName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role role;
    @Column(nullable = false)
    private boolean active = true;

    protected User() {}
    public User(String username, String email, String password, String fullName, Role role) {
        this.username = username; this.email = email; this.password = password;
        this.fullName = fullName; this.role = role; this.active = true;
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/repository/UserRepository.java' @'
package com.scentstation.identity.repository;

import com.scentstation.identity.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/dto/RegisterRequest.java' @'
package com.scentstation.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 80) String username,
    @NotBlank @Email @Size(max = 160) String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank @Size(max = 120) String fullName
) {}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/dto/LoginRequest.java' @'
package com.scentstation.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/dto/AuthResponse.java' @'
package com.scentstation.identity.dto;

public record AuthResponse(String token, String tokenType, Long userId, String username, String role) {}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/service/JwtService.java' @'
package com.scentstation.identity.service;

import com.scentstation.identity.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final Key key;
    private final long expirationMs;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expiration-ms}") long expirationMs) {
        byte[] bytes = java.util.Base64.getDecoder().decode(secret);
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder().subject(user.getUsername()).claim("role", user.getRole().name())
            .issuedAt(now).expiration(new Date(now.getTime() + expirationMs)).signWith(key).compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build()
            .parseSignedClaims(token).getPayload().getSubject();
    }

    public String extractRole(String token) {
        return Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build()
            .parseSignedClaims(token).getPayload().get("role", String.class);
    }
}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/config/JwtAuthenticationFilter.java' @'
package com.scentstation.identity.config;

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
    public JwtAuthenticationFilter(JwtService jwtService) { this.jwtService = jwtService; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                String username = jwtService.extractUsername(token);
                String role = jwtService.extractRole(token);
                var authority = new SimpleGrantedAuthority("ROLE_" + role);
                var authentication = new UsernamePasswordAuthenticationToken(username, null, java.util.List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/config/SecurityConfig.java' @'
package com.scentstation.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/service/AuthService.java' @'
package com.scentstation.identity.service;

import com.scentstation.identity.dto.AuthResponse;
import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.RegisterRequest;
import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users; this.passwordEncoder = passwordEncoder; this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        if (users.existsByUsername(username)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        // Public registration always creates CUSTOMER; privileged roles must be assigned by an administrator.
        User user = new User(username, email, passwordEncoder.encode(request.password()), request.fullName().trim(), Role.CUSTOMER);
        users.save(user);
        return response(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username().trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPassword()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        return response(user);
    }

    private AuthResponse response(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer", user.getId(), user.getUsername(), user.getRole().name());
    }
}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/controller/AuthController.java' @'
package com.scentstation.identity.controller;

import com.scentstation.identity.dto.AuthResponse;
import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.RegisterRequest;
import com.scentstation.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) { return authService.register(request); }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return authService.login(request); }

    @GetMapping("/health")
    public String health() { return "Identity Service is running"; }
}
'@

Write-Utf8File 'services/identity-service/src/main/java/com/scentstation/identity/exception/GlobalExceptionHandler.java' @'
package com.scentstation.identity.exception;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("timestamp", Instant.now().toString(), "status", ex.getStatusCode().value(), "error", ex.getReason() == null ? "Request failed" : ex.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty() ? "Invalid request" : ex.getBindingResult().getFieldErrors().get(0).getField() + ": " + ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now().toString(), "status", 400, "error", message));
    }
}
'@

Write-Utf8File 'services/api-gateway/pom.xml' @'
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.3.5</version><relativePath/></parent>
  <groupId>com.scentstation</groupId><artifactId>api-gateway</artifactId><version>0.0.1-SNAPSHOT</version>
  <properties><java.version>17</java.version><spring-cloud.version>2023.0.3</spring-cloud.version></properties>
  <dependencyManagement><dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-dependencies</artifactId><version>${spring-cloud.version}</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
  <dependencies>
    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-gateway</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
  </dependencies>
  <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
</project>
'@

Write-Utf8File 'services/api-gateway/src/main/java/com/scentstation/gateway/ApiGatewayApplication.java' @'
package com.scentstation.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) { SpringApplication.run(ApiGatewayApplication.class, args); }
}
'@

Write-Utf8File 'services/api-gateway/src/main/resources/application.yml' @'
server:
  port: 8080
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: identity-service
          uri: ${IDENTITY_SERVICE_URL:http://localhost:8081}
          predicates:
            - Path=/api/auth/**,/api/admin/**
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}
            allowedMethods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
            allowedHeaders: "*"
            allowCredentials: true
management:
  endpoints:
    web:
      exposure:
        include: health,info
'@

Write-Utf8File 'docs/identity-platform.md' @'
# Identity Service và API Gateway

## Thành phần
- Identity Service: Spring Boot, Spring Security, Spring Data JPA, MySQL.
- Xác thực: JWT Bearer token; mật khẩu lưu dưới dạng BCrypt hash.
- Vai trò: ADMIN, STAFF, CUSTOMER. Đăng ký công khai luôn tạo CUSTOMER; không cho người dùng tự đăng ký quyền quản trị.
- API Gateway: cổng 8080, định tuyến `/api/auth/**` và `/api/admin/**` tới Identity Service cổng 8081.

## API
- `POST /api/auth/register`: `{ "username": "demo", "email": "demo@example.com", "password": "Password123", "fullName": "Demo User" }`
- `POST /api/auth/login`: `{ "username": "demo", "password": "Password123" }`
- `GET /api/auth/health`: kiểm tra Identity Service.

Đăng ký và đăng nhập trả về token trong trường `token`. Gửi `Authorization: Bearer <token>` khi gọi API cần xác thực.

## Chạy local
1. Cần Java 17+, Maven và MySQL đang chạy.
2. Tạo database `scentstation_identity` hoặc cho phép JDBC tạo database.
3. Đặt `DB_USERNAME`, `DB_PASSWORD`; không dùng mật khẩu mặc định ngoài môi trường local.
4. Trong `services/identity-service`, chạy `mvn spring-boot:run`.
5. Trong terminal khác, tại `services/api-gateway`, chạy `mvn spring-boot:run`.
6. Gọi Gateway tại `http://localhost:8080`.

## Lưu ý bảo mật
- Thay `JWT_SECRET` bằng secret Base64 ngẫu nhiên đủ mạnh ở môi trường triển khai.
- Cấu hình CORS theo đúng origin của frontend.
- Tài khoản ADMIN/STAFF cần được tạo bởi quy trình quản trị đáng tin cậy; không cho phép chọn role trong endpoint đăng ký công khai.
- Cấu hình JPA `ddl-auto: update` chỉ tiện cho phát triển local; môi trường thật nên dùng migration.
'@

Write-Host 'Da ghi ma nguon vao cac file.' -ForegroundColor Green
Write-Host 'Kiem tra Java va Maven truoc khi build:' -ForegroundColor Cyan
java -version
mvn -version

Write-Host "`nBuild Identity Service..." -ForegroundColor Cyan
Push-Location services/identity-service
mvn -q -DskipTests package
$identityBuild = $LASTEXITCODE
Pop-Location
if ($identityBuild -ne 0) { throw 'Identity Service build that bai. Khong commit/push; xem loi Maven o tren.' }

Write-Host "`nBuild API Gateway..." -ForegroundColor Cyan
Push-Location services/api-gateway
mvn -q -DskipTests package
$gatewayBuild = $LASTEXITCODE
Pop-Location
if ($gatewayBuild -ne 0) { throw 'API Gateway build that bai. Khong commit/push; xem loi Maven o tren.' }

Write-Host "`nBuild thanh cong. Xem thay doi truoc khi commit:" -ForegroundColor Green
git status --short
git diff --stat
Write-Host "`nNeu ket qua dung, chay git add/commit/push rieng tren nhanh nay." -ForegroundColor Yellow
