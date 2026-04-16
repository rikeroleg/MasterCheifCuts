package com.masterchefcuts.controllers;

import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.services.StripeConnectService;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConnectController.class)
@AutoConfigureMockMvc(addFilters = false)
class ConnectControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean StripeConnectService stripeConnectService;
    @MockBean ParticipantRepo participantRepo;
    @MockBean JwtUtil jwtUtil;

    private static final Participant ONBOARDED_FARMER = Participant.builder()
            .id("farmer-1").firstName("Jane").lastName("Farm")
            .role(Role.FARMER).email("jane@farm.com").password("pass")
            .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
            .status("ACTIVE").approved(true)
            .stripeAccountId("acct_123")
            .stripeOnboardingComplete(true)
            .build();

    private static final Participant NEW_FARMER = Participant.builder()
            .id("farmer-2").firstName("Bob").lastName("Ranch")
            .role(Role.FARMER).email("bob@ranch.com").password("pass")
            .street("2 Ranch Rd").city("Rural").state("TX").zipCode("54321")
            .status("ACTIVE").approved(true)
            .stripeAccountId(null)
            .stripeOnboardingComplete(false)
            .build();

    private UsernamePasswordAuthenticationToken farmerAuth(String id) {
        return new UsernamePasswordAuthenticationToken(id, null,
                List.of(new SimpleGrantedAuthority("ROLE_FARMER")));
    }

    // ── POST /api/connect/onboard ─────────────────────────────────────────────

    @Test
    void onboard_newFarmer_returns200WithUrl() throws Exception {
        when(stripeConnectService.createOnboardingLink("farmer-2"))
                .thenReturn("https://connect.stripe.com/setup/e/acct_new/onboard");
        SecurityContextHolder.getContext().setAuthentication(farmerAuth("farmer-2"));
        try {
            mockMvc.perform(post("/api/connect/onboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value("https://connect.stripe.com/setup/e/acct_new/onboard"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void onboard_existingFarmer_returnsRefreshUrl() throws Exception {
        when(stripeConnectService.createOnboardingLink("farmer-1"))
                .thenReturn("https://connect.stripe.com/setup/e/acct_123/refresh");
        SecurityContextHolder.getContext().setAuthentication(farmerAuth("farmer-1"));
        try {
            mockMvc.perform(post("/api/connect/onboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value("https://connect.stripe.com/setup/e/acct_123/refresh"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void onboard_stripeError_returns400() throws Exception {
        StripeException ex = mock(StripeException.class);
        when(ex.getMessage()).thenReturn("Stripe API error");
        when(stripeConnectService.createOnboardingLink("farmer-1")).thenThrow(ex);
        SecurityContextHolder.getContext().setAuthentication(farmerAuth("farmer-1"));
        try {
            mockMvc.perform(post("/api/connect/onboard"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── GET /api/connect/dashboard ────────────────────────────────────────────

    @Test
    void dashboard_onboardedFarmer_returns200WithUrl() throws Exception {
        when(stripeConnectService.createDashboardLink("farmer-1"))
                .thenReturn("https://connect.stripe.com/express/acct_123/dashboard");
        SecurityContextHolder.getContext().setAuthentication(farmerAuth("farmer-1"));
        try {
            mockMvc.perform(get("/api/connect/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value("https://connect.stripe.com/express/acct_123/dashboard"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void dashboard_notOnboarded_returns400() throws Exception {
        when(stripeConnectService.createDashboardLink("farmer-2"))
                .thenThrow(new IllegalArgumentException("Stripe account not ready — please complete onboarding first"));
        SecurityContextHolder.getContext().setAuthentication(farmerAuth("farmer-2"));
        try {
            mockMvc.perform(get("/api/connect/dashboard"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── GET /api/connect/status ───────────────────────────────────────────────

    @Test
    void status_onboardedFarmer_returnsCompleteStatus() throws Exception {
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(ONBOARDED_FARMER));
        SecurityContextHolder.getContext().setAuthentication(farmerAuth("farmer-1"));
        try {
            mockMvc.perform(get("/api/connect/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stripeAccountId").value("acct_123"))
                    .andExpect(jsonPath("$.stripeOnboardingComplete").value(true));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void status_newFarmer_returnsIncompleteStatus() throws Exception {
        when(participantRepo.findById("farmer-2")).thenReturn(Optional.of(NEW_FARMER));
        SecurityContextHolder.getContext().setAuthentication(farmerAuth("farmer-2"));
        try {
            mockMvc.perform(get("/api/connect/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stripeAccountId").value(""))
                    .andExpect(jsonPath("$.stripeOnboardingComplete").value(false));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void status_farmerNotFound_returns400() throws Exception {
        when(participantRepo.findById("unknown")).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(farmerAuth("unknown"));
        try {
            mockMvc.perform(get("/api/connect/status"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
