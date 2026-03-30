package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.AuthResponse;
import com.masterchefcuts.dto.LoginRequest;
import com.masterchefcuts.dto.RegisterRequest;
import com.masterchefcuts.services.AuthService;
import com.masterchefcuts.services.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal String participantId) {
        return ResponseEntity.ok(authService.getMe(participantId));
    }

    @PatchMapping("/me")
    public ResponseEntity<AuthResponse> updateMe(@AuthenticationPrincipal String participantId,
                                                  @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.updateProfile(participantId, req));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestParam String email) {
        authService.forgotPassword(email, emailService);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestParam String token,
                                               @RequestParam String password) {
        authService.resetPassword(token, password);
        return ResponseEntity.ok().build();
    }
}
