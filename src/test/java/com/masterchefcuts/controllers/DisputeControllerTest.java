package com.masterchefcuts.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.DisputeRequest;
import com.masterchefcuts.model.Dispute;
import com.masterchefcuts.services.DisputeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DisputeController.class)
@AutoConfigureMockMvc(addFilters = false)
class DisputeControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean DisputeService disputeService;
    @MockBean JwtUtil jwtUtil;

    private void authAs(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    // ── POST /api/disputes ────────────────────────────────────────────────────

    @Test
    void createDispute_buyerReturns200() throws Exception {
        Dispute saved = Dispute.builder()
                .id(1L).buyerId("buyer-1").claimId(5L).listingId(10L)
                .type("QUALITY").description("Cuts were poor quality").status("OPEN").build();

        when(disputeService.createDispute(eq("buyer-1"), eq(5L), eq(10L), eq("QUALITY"), anyString()))
                .thenReturn(saved);

        DisputeRequest req = new DisputeRequest();
        req.setClaimId(5L);
        req.setListingId(10L);
        req.setType("QUALITY");
        req.setDescription("Cuts were poor quality");

        authAs("buyer-1", "BUYER");
        try {
            mockMvc.perform(post("/api/disputes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("OPEN"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createDispute_duplicateOpenDispute_returns409() throws Exception {
        when(disputeService.createDispute(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("An open dispute already exists for this claim"));

        DisputeRequest req = new DisputeRequest();
        req.setClaimId(5L);
        req.setListingId(10L);
        req.setType("QUALITY");
        req.setDescription("Same problem again");

        authAs("buyer-1", "BUYER");
        try {
            mockMvc.perform(post("/api/disputes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── GET /api/admin/disputes ───────────────────────────────────────────────

    @Test
    void getAllDisputes_adminReturns200() throws Exception {
        Dispute d1 = Dispute.builder().id(1L).status("OPEN").type("QUALITY").build();
        Dispute d2 = Dispute.builder().id(2L).status("RESOLVED").type("BILLING").build();

        when(disputeService.getAllDisputes()).thenReturn(List.of(d1, d2));

        authAs("admin-1", "ADMIN");
        try {
            mockMvc.perform(get("/api/admin/disputes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[1].status").value("RESOLVED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── PATCH /api/admin/disputes/{id}/resolve ────────────────────────────────

    @Test
    void resolveDispute_adminReturns200() throws Exception {
        Dispute resolved = Dispute.builder()
                .id(1L).status("RESOLVED").resolution("Refund processed").build();

        when(disputeService.resolveDispute(1L, "Refund processed")).thenReturn(resolved);

        authAs("admin-1", "ADMIN");
        try {
            mockMvc.perform(patch("/api/admin/disputes/1/resolve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("resolution", "Refund processed"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RESOLVED"))
                    .andExpect(jsonPath("$.resolution").value("Refund processed"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void resolveDispute_notFound_returns404() throws Exception {
        when(disputeService.resolveDispute(eq(99L), anyString()))
                .thenThrow(new IllegalArgumentException("Dispute not found: 99"));

        authAs("admin-1", "ADMIN");
        try {
            mockMvc.perform(patch("/api/admin/disputes/99/resolve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("resolution", "Noted"))))
                    .andExpect(status().isNotFound());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
