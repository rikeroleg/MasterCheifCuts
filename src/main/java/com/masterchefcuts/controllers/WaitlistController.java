package com.masterchefcuts.controllers;

import com.masterchefcuts.services.WaitlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PreAuthorize("hasRole('BUYER')")
    @PostMapping("/api/listings/{listingId}/waitlist")
    public ResponseEntity<Map<String, Object>> join(
            @PathVariable Long listingId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(waitlistService.join(listingId, userId));
    }

    @PreAuthorize("hasRole('BUYER')")
    @DeleteMapping("/api/listings/{listingId}/waitlist")
    public ResponseEntity<Void> leave(
            @PathVariable Long listingId,
            @AuthenticationPrincipal String userId) {
        waitlistService.leave(listingId, userId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/listings/{listingId}/waitlist/status")
    public ResponseEntity<Map<String, Object>> status(
            @PathVariable Long listingId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(waitlistService.status(listingId, userId));
    }
}
