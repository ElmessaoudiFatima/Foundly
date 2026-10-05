package com.backend.controller;

import com.backend.dto.request.LoginRequest;
import com.backend.dto.request.RefreshRequest;
import com.backend.dto.request.RegisterRequest;
import com.backend.dto.response.AuthSessionResponse;
import com.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Contrôleur d'authentification — aucune logique métier, uniquement délégation.
 * Aucune injection de Repository. Aucune manipulation d'entité.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<AuthSessionResponse> register(@Valid @RequestBody RegisterRequest req) {
        AuthSessionResponse session = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    /**
     * POST /auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthSessionResponse session = authService.login(req);
        return ResponseEntity.ok(session);
    }

    /**
     * POST /auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        AuthSessionResponse session = authService.refresh(req.refreshToken());
        return ResponseEntity.ok(session);
    }

    /**
     * POST /auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        UUID userId = UUID.fromString(userDetails.getUsername());
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}
