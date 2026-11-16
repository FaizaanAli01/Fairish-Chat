package com.fairish.chat.controller;

import com.fairish.chat.dto.AuthResponse;
import com.fairish.chat.dto.LoginRequest;
import com.fairish.chat.dto.RegisterRequest;
import com.fairish.chat.exception.TokenBlacklistException;
import com.fairish.chat.exception.UnauthorizedException;
import com.fairish.chat.security.JwtService;
import com.fairish.chat.security.TokenBlacklistService;
import com.fairish.chat.service.AuthService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtService jwtService;

    public AuthController(AuthService authService,
                          TokenBlacklistService tokenBlacklistService,
                          JwtService jwtService) {
        this.authService = authService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String token = extractBearerToken(request);
        Date expiration = validateToken(token);

        try {
            tokenBlacklistService.blacklistToken(token, expiration.toInstant());
        } catch (RuntimeException ex) {
            throw new TokenBlacklistException("Failed to revoke the authentication token.", ex);
        }

        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            throw new UnauthorizedException("Authorization header with a Bearer token is required.");
        }

        String[] parts = authorization.trim().split("\\s+", 2);
        if (parts.length != 2
                || !"Bearer".equalsIgnoreCase(parts[0])
                || !StringUtils.hasText(parts[1])) {
            throw new UnauthorizedException("Authorization header must use the Bearer scheme.");
        }

        return parts[1].trim();
    }

    private Date validateToken(String token) {
        Date expiration;
        try {
            // Parsing the signed JWT verifies its signature and expiration before
            // any claims are read.
            expiration = jwtService.extractExpiration(token);
        } catch (ExpiredJwtException ex) {
            throw new UnauthorizedException("The authentication token has expired.");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("The authentication token is invalid.");
        }

        if (expiration == null || !expiration.after(new Date())) {
            throw new UnauthorizedException("The authentication token has expired.");
        }

        return expiration;
    }
}
