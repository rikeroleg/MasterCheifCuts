package com.masterchefcuts.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.ReviewRequest;
import com.masterchefcuts.dto.ReviewResponse;
import com.masterchefcuts.services.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReviewControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ReviewService reviewService;
    @MockBean JwtUtil jwtUtil;

    private final ReviewResponse SAMPLE = ReviewResponse.builder()
            .id(1L).listingId(1L).buyerName("Bob B.").rating(5)
            .comment("Great beef!").createdAt(LocalDateTime.now()).build();

    // ── GET /api/listings/{listingId}/reviews ─────────────────────────────────

    @Test
    void getReviews_returns200WithList() throws Exception {
        when(reviewService.getReviewsForListing(1L)).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/listings/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].buyerName").value("Bob B."));
    }

    @Test
    void getReviews_emptyList_returns200() throws Exception {
        when(reviewService.getReviewsForListing(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/listings/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST /api/reviews ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "buyer-1", roles = {"BUYER"})
    void createReview_returns200WithReviewResponse() throws Exception {
        when(reviewService.createReview(any(), any(ReviewRequest.class))).thenReturn(SAMPLE);

        ReviewRequest req = new ReviewRequest();
        req.setListingId(1L);
        req.setRating(5);
        req.setComment("Great beef!");

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value("Great beef!"));
    }

    @Test
    @WithMockUser(username = "buyer-1", roles = {"BUYER"})
    void createReview_serviceThrows_returns400() throws Exception {
        when(reviewService.createReview(any(), any())).thenThrow(new RuntimeException("Already reviewed"));

        ReviewRequest req = new ReviewRequest();
        req.setListingId(1L);
        req.setRating(3);

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Already reviewed"));
    }

    // ── GET /api/reviews/farmer/{farmerId} ────────────────────────────────────

    @Test
    void getFarmerReviews_returns200WithList() throws Exception {
        when(reviewService.getReviewsForFarmer("farmer-1")).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/reviews/farmer/farmer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].buyerName").value("Bob B."));
    }

    @Test
    void getFarmerReviews_emptyList_returns200() throws Exception {
        when(reviewService.getReviewsForFarmer("farmer-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/reviews/farmer/farmer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /api/reviews/has-reviewed ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "buyer-1", roles = {"BUYER"})
    void hasReviewed_returnsTrue_whenReviewExists() throws Exception {
        when(reviewService.hasReviewed("buyer-1", 1L)).thenReturn(true);

        mockMvc.perform(get("/api/reviews/has-reviewed").param("listingId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    @Test
    @WithMockUser(username = "buyer-1", roles = {"BUYER"})
    void hasReviewed_returnsFalse_whenNoReview() throws Exception {
        when(reviewService.hasReviewed("buyer-1", 1L)).thenReturn(false);

        mockMvc.perform(get("/api/reviews/has-reviewed").param("listingId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));
    }

    @Test
    void hasReviewed_noAuth_returnsFalse() throws Exception {
        mockMvc.perform(get("/api/reviews/has-reviewed").param("listingId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));
    }
}
