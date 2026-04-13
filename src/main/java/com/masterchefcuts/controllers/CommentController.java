package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.CommentResponse;
import com.masterchefcuts.services.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/listings/{listingId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long listingId) {
        return ResponseEntity.ok(commentService.getComments(listingId));
    }

    @PostMapping
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable Long listingId,
            @AuthenticationPrincipal String authorId,
            @RequestBody Map<String, String> body) {
        String text = body.get("body");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(commentService.addComment(listingId, authorId, text.trim()));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long listingId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal String requesterId) {
        String role = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");
        commentService.deleteComment(commentId, requesterId, role);
        return ResponseEntity.noContent().build();
    }
}
