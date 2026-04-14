package com.masterchefcuts.controllers;

import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.NotificationResponse;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.services.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean NotificationService notificationService;
    @MockBean JwtUtil jwtUtil;

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken("user-1", null, List.of());
    }

    private final NotificationResponse SAMPLE = NotificationResponse.builder()
            .id(1L).type(NotificationType.CUT_CLAIMED).icon("🛒")
            .title("Cut claimed").body("You claimed Ribeye").read(false)
            .listingId(1L).createdAt(LocalDateTime.now()).build();
    // ── GET /api/notifications/stream ──────────────────────────────────────────

    @Test
    void stream_returns200ForAuthenticatedUser() throws Exception {
        when(notificationService.subscribe("user-1")).thenReturn(new SseEmitter(Long.MAX_VALUE));

        mockMvc.perform(get("/api/notifications/stream").with(authentication(auth())))
                .andExpect(status().isOk());
    }
    // ── GET /api/notifications ────────────────────────────────────────────────

    @Test
    void getAll_returns200WithList() throws Exception {
        when(notificationService.getForRecipient(any())).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/notifications").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Cut claimed"))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    // ── GET /api/notifications/unread-count ───────────────────────────────────

    @Test
    void unreadCount_returns200WithCount() throws Exception {
        when(notificationService.getUnreadCount(any())).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread-count").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    // ── POST /api/notifications/{id}/read ─────────────────────────────────────

    @Test
    void markRead_returns204() throws Exception {
        doNothing().when(notificationService).markRead(1L, "user-1");

        mockMvc.perform(post("/api/notifications/1/read").with(authentication(auth())))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/notifications/read-all ──────────────────────────────────────

    @Test
    void markAllRead_returns204() throws Exception {
        doNothing().when(notificationService).markAllRead("user-1");

        mockMvc.perform(post("/api/notifications/read-all").with(authentication(auth())))
                .andExpect(status().isNoContent());
    }

    // ── DELETE /api/notifications ─────────────────────────────────────────────

    @Test
    void clearAll_returns204() throws Exception {
        doNothing().when(notificationService).clearAll("user-1");

        mockMvc.perform(delete("/api/notifications").with(authentication(auth())))
                .andExpect(status().isNoContent());
    }
}
