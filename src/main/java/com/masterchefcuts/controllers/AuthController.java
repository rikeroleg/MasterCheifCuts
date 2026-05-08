package com.masterchefcuts.controllers;

import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.AuthResponse;
import com.masterchefcuts.dto.EmailRequest;
import com.masterchefcuts.dto.LoginRequest;
import com.masterchefcuts.dto.RegisterRequest;
import com.masterchefcuts.dto.ResetPasswordRequest;
import com.masterchefcuts.dto.UpdateProfileRequest;
import com.masterchefcuts.services.AuthService;
import com.masterchefcuts.services.EmailService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    @Value("${security.cookie.secure:true}")
    private boolean secureCookie;

    /** Set httpOnly auth cookies on the response. Tokens are NOT returned in the JSON body. */
    private void setAuthCookies(HttpServletResponse response, AuthResponse auth) {
        if (auth.getToken() != null) {
            ResponseCookie access = ResponseCookie.from("mc_auth", auth.getToken())
                    .httpOnly(true)
                    .secure(secureCookie)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(jwtUtil.getExpirationMs() / 1000)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, access.toString());
            auth.setToken(null);
        }
        if (auth.getRefreshToken() != null) {
            ResponseCookie refresh = ResponseCookie.from("mc_refresh", auth.getRefreshToken())
                    .httpOnly(true)
                    .secure(secureCookie)
                    .sameSite("Strict")
                    .path("/api/auth/refresh")
                    .maxAge(7 * 24 * 60 * 60L)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, refresh.toString());
            auth.setRefreshToken(null);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req,
                                                  HttpServletResponse response) {
        AuthResponse auth = authService.register(req, emailService);
        setAuthCookies(response, auth);
        return ResponseEntity.ok(auth);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody EmailRequest req) {
        authService.resendVerification(req.getEmail(), emailService);
        return ResponseEntity.ok(Map.of("message", "Verification email sent."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletResponse response) {
        AuthResponse auth = authService.login(req);
        setAuthCookies(response, auth);
        return ResponseEntity.ok(auth);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal String participantId) {
        if (participantId == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.getMe(participantId));
    }

    @PatchMapping("/me")
    public ResponseEntity<AuthResponse> updateMe(@AuthenticationPrincipal String participantId,
                                                  @Valid @RequestBody UpdateProfileRequest req) {
        if (participantId == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.updateProfile(participantId, req));
    }

    /** Refresh: reads the refresh token from the httpOnly mc_refresh cookie. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "mc_refresh", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        AuthResponse auth = authService.refreshToken(refreshToken);
        setAuthCookies(response, auth);
        return ResponseEntity.ok(auth);
    }

    /** Logout: clears both auth cookies. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        ResponseCookie clearAccess = ResponseCookie.from("mc_auth", "")
                .httpOnly(true).secure(secureCookie).sameSite("Strict").path("/").maxAge(0).build();
        ResponseCookie clearRefresh = ResponseCookie.from("mc_refresh", "")
                .httpOnly(true).secure(secureCookie).sameSite("Strict").path("/api/auth/refresh").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearAccess.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefresh.toString());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody EmailRequest req) {
        authService.forgotPassword(req.getEmail(), emailService);
        return ResponseEntity.ok(Map.of("message", "If that email is registered you will receive a reset link."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.getToken(), req.getPassword());
        return ResponseEntity.ok().build();
    }
}
