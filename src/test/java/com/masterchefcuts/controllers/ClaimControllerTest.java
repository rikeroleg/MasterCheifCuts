package com.masterchefcuts.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.ClaimRequest;
import com.masterchefcuts.dto.ClaimResponse;
import com.masterchefcuts.dto.ListingResponse;
import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.model.Claim;
import com.masterchefcuts.services.ClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClaimController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClaimControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ClaimService claimService;
    @MockBean JwtUtil jwtUtil;

    private UsernamePasswordAuthenticationToken buyerAuth() {
        return new UsernamePasswordAuthenticationToken("buyer-1", null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
    }

    private UsernamePasswordAuthenticationToken farmerAuth() {
        return new UsernamePasswordAuthenticationToken("farmer-1", null,
                List.of(new SimpleGrantedAuthority("ROLE_FARMER")));
    }

    // ── POST /api/listings/{listingId}/claims ─────────────────────────────────

    @Test
    void claimCut_returns200WithListingResponse() throws Exception {
        ListingResponse response = ListingResponse.builder()
                .id(1L).animalType(AnimalType.BEEF).breed("Angus")
                .status(ListingStatus.ACTIVE).farmerId("farmer-1")
                .farmerName("Jane Farm").postedAt(LocalDateTime.now())
                .cuts(List.of()).zipCode("12345").totalCuts(2).claimedCuts(1).build();

        when(claimService.claimCut(anyLong(), anyLong(), any())).thenReturn(response);

        ClaimRequest req = new ClaimRequest();
        req.setCutId(10L);

        mockMvc.perform(post("/api/listings/1/claims")
                        .with(authentication(buyerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void claimCut_serviceThrows_returns400() throws Exception {
        when(claimService.claimCut(anyLong(), anyLong(), any()))
                .thenThrow(new RuntimeException("Cut is already claimed"));

        ClaimRequest req = new ClaimRequest();
        req.setCutId(10L);

        mockMvc.perform(post("/api/listings/1/claims")
                        .with(authentication(buyerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cut is already claimed"));
    }

    // ── GET /api/listings/{listingId}/claims ──────────────────────────────────

    @Test
    void getClaimsForListing_returns200WithList() throws Exception {
        when(claimService.getClaimsForListing(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/listings/1/claims")
                        .with(authentication(farmerAuth())))
                .andExpect(status().isOk());
    }

    // ── GET /api/claims/my ────────────────────────────────────────────────────

    @Test
    void getMyClaims_returns200WithClaimResponses() throws Exception {
        ClaimResponse claimResponse = ClaimResponse.builder()
                .id(1L).listingId(1L).breed("Angus").cutId(10L).cutLabel("Ribeye")
                .animalType(AnimalType.BEEF).listingStatus(ListingStatus.ACTIVE)
                .zipCode("12345").claimedAt(LocalDateTime.now()).build();

        when(claimService.getClaimResponsesForBuyer(any())).thenReturn(List.of(claimResponse));

        mockMvc.perform(get("/api/claims/my")
                        .with(authentication(buyerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cutLabel").value("Ribeye"));
    }
}
