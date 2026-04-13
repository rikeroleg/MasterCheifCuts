package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.MessageResponse;
import com.masterchefcuts.dto.MessageThreadResponse;
import com.masterchefcuts.services.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** GET /api/messages/threads — list latest message per conversation */
    @GetMapping("/threads")
    public ResponseEntity<List<MessageThreadResponse>> getThreads(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(messageService.getThreads(userId));
    }

    /** GET /api/messages?with={participantId} — full conversation */
    @GetMapping
    public ResponseEntity<List<MessageResponse>> getConversation(
            @AuthenticationPrincipal String userId,
            @RequestParam("with") String withId) {
        return ResponseEntity.ok(messageService.getConversation(userId, withId));
    }

    /** POST /api/messages — send a message */
    @PostMapping
    public ResponseEntity<MessageResponse> send(
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, String> body) {
        String to = body.get("recipientId");
        String content = body.get("content");
        if (to == null || to.isBlank())
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(messageService.send(userId, to, content));
    }

    /** POST /api/messages/{id}/read — mark a single message as read */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false, name = "with") String ignored) {
        messageService.markRead(id, userId);
        return ResponseEntity.noContent().build();
    }
}
