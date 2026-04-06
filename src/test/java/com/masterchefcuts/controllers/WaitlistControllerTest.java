package com.masterchefcuts.controllers;

import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.services.WaitlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WaitlistController.class)
@AutoConfigureMockMvc(addFilters = false)
class WaitlistControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean WaitlistService waitlistService;
    @MockBean JwtUtil jwtUtil;

    // ── POST /api/listings/{listingId}/waitlist ───────────────────────────────

    @Test
    @WithMockUser(username = "buyer-1", roles = {"BUYER"})
    void join_returns200WithStatus() throws Exception {
        when(waitlistService.join(eq(1L), any())).thenReturn(Map.of("onWaitlist", true, "position", 1L));

        mockMvc.perform(post("/api/listings/1/waitlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onWaitlist").value(true));
    }

    @Test
    @WithMockUser(username = "buyer-1", roles = {"BUYER"})
    void join_serviceThrows_returns400() throws Exception {
        when(waitlistService.join(eq(99L), any())).thenThrow(new RuntimeException("Listing not found"));

        mockMvc.perform(post("/api/listings/99/waitlist"))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/listings/{listingId}/waitlist ─────────────────────────────

    @Test
    @WithMockUser(username = "buyer-1", roles = {"BUYER"})
    void leave_returns204() throws Exception {
        doNothing().when(waitlistService).leave(anyLong(), any());

        mockMvc.perform(delete("/api/listings/1/waitlist"))
                .andExpect(status().isNoContent());
    }

    // ── GET /api/listings/{listingId}/waitlist/status ─────────────────────────

    @Test
    @WithMockUser(username = "buyer-1", roles = {"BUYER"})
    void status_returns200WithStatusMap() throws Exception {
        when(waitlistService.status(eq(1L), any()))
                .thenReturn(Map.of("onWaitlist", false, "total", 0L));

        mockMvc.perform(get("/api/listings/1/waitlist/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onWaitlist").value(false))
                .andExpect(jsonPath("$.total").value(0));
    }
}
