package com.masterchefcuts.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.AuthResponse;
import com.masterchefcuts.dto.LoginRequest;
import com.masterchefcuts.dto.RegisterRequest;
import com.masterchefcuts.dto.ResetPasswordRequest;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.services.AuthService;
import com.masterchefcuts.services.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean EmailService emailService;
    @MockBean JwtUtil jwtUtil;

    private static final AuthResponse SAMPLE_RESPONSE = AuthResponse.builder()
            .id("user-1").firstName("John").lastName("Doe")
            .email("john@example.com").role(Role.BUYER).approved(true).build();

    private UsernamePasswordAuthenticationToken stringAuth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_returns200WithBody() throws Exception {
        when(authService.register(any(RegisterRequest.class), any(EmailService.class)))
                .thenReturn(SAMPLE_RESPONSE);

        RegisterRequest req = buildRegisterRequest();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void register_serviceThrows_returns400() throws Exception {
        when(authService.register(any(), any())).thenThrow(new RuntimeException("An account with that email already exists."));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegisterRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("An account with that email already exists."));
    }

    // ── verifyEmail ───────────────────────────────────────────────────────────

    @Test
    void verifyEmail_returns200() throws Exception {
        doNothing().when(authService).verifyEmail("my-token");

        mockMvc.perform(get("/api/auth/verify-email").param("token", "my-token"))
                .andExpect(status().isOk());
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_returns200WithToken() throws Exception {
        AuthResponse withToken = AuthResponse.builder()
                .id("user-1").token("jwt-token").role(Role.BUYER).approved(true)
                .firstName("John").lastName("Doe").email("john@example.com").build();
        when(authService.login(any(LoginRequest.class))).thenReturn(withToken);

        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    // ── me ────────────────────────────────────────────────────────────────────

    @Test
    void me_returns200WithParticipantData() throws Exception {
        when(authService.getMe(any())).thenReturn(SAMPLE_RESPONSE);
        SecurityContextHolder.getContext().setAuthentication(stringAuth("user-1"));
        try {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("user-1"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── updateMe ──────────────────────────────────────────────────────────────

    @Test
    void updateMe_returns200() throws Exception {
        when(authService.updateProfile(any(), any(RegisterRequest.class)))
                .thenReturn(SAMPLE_RESPONSE);
        SecurityContextHolder.getContext().setAuthentication(stringAuth("user-1"));
        try {
            mockMvc.perform(patch("/api/auth/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstName\":\"Jane\"}"))
                    .andExpect(status().isOk());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── forgotPassword ────────────────────────────────────────────────────────

    @Test
    void forgotPassword_returns200() throws Exception {
        doNothing().when(authService).forgotPassword(eq("john@example.com"), any(EmailService.class));

        mockMvc.perform(post("/api/auth/forgot-password").param("email", "john@example.com"))
                .andExpect(status().isOk());
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_returns200() throws Exception {
        doNothing().when(authService).resetPassword("reset-token", "NewPass1!");

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("reset-token");
        req.setPassword("NewPass1!");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setEmail("john@example.com");
        req.setPassword("Test@1234");
        req.setRole(Role.BUYER);
        req.setStreet("123 Main St");
        req.setCity("Springfield");
        req.setState("IL");
        req.setZipCode("62701");
        return req;
    }
}
