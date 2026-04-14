package com.masterchefcuts.controllers;

import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.services.ReferralService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReferralController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReferralControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ReferralService referralService;
    @MockBean JwtUtil jwtUtil;

    private void auth(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    // ── GET /api/referrals/my ─────────────────────────────────────────────────

    @Test
    void getMyStats_returns200WithStats() throws Exception {
        when(referralService.getMyStats("user-1")).thenReturn(
                Map.of("totalReferrals", 3, "activeReferrals", 2));

        auth("user-1");
        try {
            mockMvc.perform(get("/api/referrals/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalReferrals").value(3))
                    .andExpect(jsonPath("$.activeReferrals").value(2));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void getMyStats_noReferrals_returnsZeros() throws Exception {
        when(referralService.getMyStats("user-1")).thenReturn(
                Map.of("totalReferrals", 0, "activeReferrals", 0));

        auth("user-1");
        try {
            mockMvc.perform(get("/api/referrals/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalReferrals").value(0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
