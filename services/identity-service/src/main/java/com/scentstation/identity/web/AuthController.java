package com.scentstation.identity.web;

import com.scentstation.identity.auth.AuthDto;
import com.scentstation.identity.auth.AuthenticationService;
import com.scentstation.identity.auth.LoginOutcome;
import com.scentstation.identity.auth.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationService authentication;
    private final SessionService sessions;

    public AuthController(AuthenticationService authentication, SessionService sessions) {
        this.authentication = authentication;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        LoginOutcome result = authentication.login(request);
        if (result.succeeded()) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.success());
        }
        String message = result.status() == 423 ? "Account temporarily locked" : "Invalid credentials";
        return ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore())
                .body(new AuthDto.ErrorResponse(result.errorCode(), message));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDto.Identity> me(Authentication principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body((AuthDto.Identity) principal.getPrincipal());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        sessions.revoke(authorization.substring("Bearer ".length()));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
