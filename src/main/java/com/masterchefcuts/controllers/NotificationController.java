package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.NotificationPageResponse;
import com.masterchefcuts.dto.NotificationResponse;
import com.masterchefcuts.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getAll(@AuthenticationPrincipal String participantId) {
        return ResponseEntity.ok(notificationService.getForRecipient(participantId));
    }

    @GetMapping("/paged")
    public ResponseEntity<NotificationPageResponse> getPaged(
            @AuthenticationPrincipal String participantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.getForRecipientPaged(participantId, page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal String participantId) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(participantId)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id,
                                          @AuthenticationPrincipal String participantId) {
        notificationService.markRead(id, participantId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal String participantId) {
        notificationService.markAllRead(participantId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll(@AuthenticationPrincipal String participantId) {
        notificationService.clearAll(participantId);
        return ResponseEntity.noContent().build();
    }
}
