package com.masterchefcuts.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.CommentResponse;
import com.masterchefcuts.services.CommentService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
class CommentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean CommentService commentService;
    @MockBean JwtUtil jwtUtil;

    private static final CommentResponse SAMPLE = CommentResponse.builder()
            .id(1L).authorId("user-1").authorName("Alice Smith")
            .body("Great listing!").createdAt(LocalDateTime.now()).build();

    private UsernamePasswordAuthenticationToken buyerAuth() {
        return new UsernamePasswordAuthenticationToken("user-1", null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken("admin-1", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ── GET /api/listings/{listingId}/comments ────────────────────────────────

    @Test
    void getComments_noPagination_returnsFullList() throws Exception {
        when(commentService.getComments(1L)).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/listings/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].authorName").value("Alice Smith"))
                .andExpect(jsonPath("$[0].body").value("Great listing!"));
    }

    @Test
    void getComments_withPageParam_returnsPagedResult() throws Exception {
        Map<String, Object> paged = Map.of(
                "content", List.of(SAMPLE),
                "page", 0, "size", 20,
                "totalElements", 1L, "hasNext", false);
        when(commentService.getCommentsPaged(1L, 0, 20)).thenReturn(paged);

        mockMvc.perform(get("/api/listings/1/comments").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getComments_emptyListing_returnsEmptyList() throws Exception {
        when(commentService.getComments(99L)).thenReturn(List.of());

        mockMvc.perform(get("/api/listings/99/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST /api/listings/{listingId}/comments ───────────────────────────────

    @Test
    void addComment_validBody_returns200() throws Exception {
        when(commentService.addComment(1L, "user-1", "Great listing!")).thenReturn(SAMPLE);
        SecurityContextHolder.getContext().setAuthentication(buyerAuth());
        try {
            mockMvc.perform(post("/api/listings/1/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("body", "Great listing!"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.body").value("Great listing!"))
                    .andExpect(jsonPath("$.authorName").value("Alice Smith"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void addComment_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/listings/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).addComment(any(), any(), any());
    }

    @Test
    void addComment_blankBody_returns400() throws Exception {
        mockMvc.perform(post("/api/listings/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "   "))))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).addComment(any(), any(), any());
    }

    // ── DELETE /api/listings/{listingId}/comments/{commentId} ─────────────────

    @Test
    void deleteComment_byOwner_returns204() throws Exception {
        doNothing().when(commentService).deleteComment(1L, "user-1", "BUYER");
        SecurityContextHolder.getContext().setAuthentication(buyerAuth());
        try {
            mockMvc.perform(delete("/api/listings/1/comments/1"))
                    .andExpect(status().isNoContent());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void deleteComment_byAdmin_returns204() throws Exception {
        doNothing().when(commentService).deleteComment(1L, "admin-1", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(adminAuth());
        try {
            mockMvc.perform(delete("/api/listings/1/comments/1"))
                    .andExpect(status().isNoContent());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void deleteComment_serviceThrows_returns400() throws Exception {
        doThrow(new RuntimeException("Not authorized"))
                .when(commentService).deleteComment(eq(1L), any(), any());
        SecurityContextHolder.getContext().setAuthentication(buyerAuth());
        try {
            mockMvc.perform(delete("/api/listings/1/comments/1"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
