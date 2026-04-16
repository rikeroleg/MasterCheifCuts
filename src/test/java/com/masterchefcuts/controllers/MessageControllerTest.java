package com.masterchefcuts.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.MessageResponse;
import com.masterchefcuts.dto.MessageThreadResponse;
import com.masterchefcuts.services.MessageService;
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

@WebMvcTest(MessageController.class)
@AutoConfigureMockMvc(addFilters = false)
class MessageControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean MessageService messageService;
    @MockBean JwtUtil jwtUtil;

    private static final MessageResponse SAMPLE_MSG = MessageResponse.builder()
            .id(1L).senderId("user-1").senderName("Alice Smith")
            .receiverId("user-2").receiverName("Bob Buyer")
            .content("Hello!").read(false).sentAt(LocalDateTime.now()).build();

    private static final MessageThreadResponse SAMPLE_THREAD = MessageThreadResponse.builder()
            .otherParticipantId("user-2").otherParticipantName("Bob Buyer")
            .lastMessage("Hello!").unreadCount(1).build();

    private UsernamePasswordAuthenticationToken userAuth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
    }

    // ── GET /api/messages/threads ─────────────────────────────────────────────

    @Test
    void getThreads_returns200WithList() throws Exception {
        when(messageService.getThreads("user-1")).thenReturn(List.of(SAMPLE_THREAD));
        SecurityContextHolder.getContext().setAuthentication(userAuth("user-1"));
        try {
            mockMvc.perform(get("/api/messages/threads"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].otherParticipantId").value("user-2"))
                    .andExpect(jsonPath("$[0].unreadCount").value(1));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void getThreads_noMessages_returnsEmptyList() throws Exception {
        when(messageService.getThreads("user-1")).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(userAuth("user-1"));
        try {
            mockMvc.perform(get("/api/messages/threads"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── GET /api/messages?with={participantId} ────────────────────────────────

    @Test
    void getConversation_returns200WithMessages() throws Exception {
        when(messageService.getConversation("user-1", "user-2")).thenReturn(List.of(SAMPLE_MSG));
        SecurityContextHolder.getContext().setAuthentication(userAuth("user-1"));
        try {
            mockMvc.perform(get("/api/messages").param("with", "user-2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].content").value("Hello!"))
                    .andExpect(jsonPath("$[0].senderId").value("user-1"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void getConversation_noHistory_returnsEmptyList() throws Exception {
        when(messageService.getConversation("user-1", "user-2")).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(userAuth("user-1"));
        try {
            mockMvc.perform(get("/api/messages").param("with", "user-2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── POST /api/messages ────────────────────────────────────────────────────

    @Test
    void send_validRequest_returns200WithMessage() throws Exception {
        when(messageService.send("user-1", "user-2", "Hello!")).thenReturn(SAMPLE_MSG);
        SecurityContextHolder.getContext().setAuthentication(userAuth("user-1"));
        try {
            mockMvc.perform(post("/api/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("recipientId", "user-2", "content", "Hello!"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("Hello!"))
                    .andExpect(jsonPath("$.receiverId").value("user-2"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void send_missingRecipient_returns400() throws Exception {
        mockMvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Hello!"))))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).send(any(), any(), any());
    }

    @Test
    void send_blankRecipient_returns400() throws Exception {
        mockMvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("recipientId", "   ", "content", "Hello!"))))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).send(any(), any(), any());
    }

    // ── POST /api/messages/{id}/read ──────────────────────────────────────────

    @Test
    void markRead_returns204() throws Exception {
        doNothing().when(messageService).markRead(1L, "user-1");
        SecurityContextHolder.getContext().setAuthentication(userAuth("user-1"));
        try {
            mockMvc.perform(post("/api/messages/1/read"))
                    .andExpect(status().isNoContent());
            verify(messageService).markRead(1L, "user-1");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
