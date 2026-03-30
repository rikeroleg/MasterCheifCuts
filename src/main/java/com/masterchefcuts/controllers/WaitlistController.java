package com.masterchefcuts.controllers;

import com.masterchefcuts.services.WaitlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(waitlistService.join(listingId, userDetails.getUsername()));
    }

    @PreAuthorize("hasRole('BUYER')")
    @DeleteMapping("/api/listings/{listingId}/waitlist")
    public ResponseEntity<Void> leave(
            @PathVariable Long listingId,
            @AuthenticationPrincipal UserDetails userDetails) {
        waitlistService.leave(listingId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/listings/{listingId}/waitlist/status")
    public ResponseEntity<Map<String, Object>> status(
            @PathVariable Long listingId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(waitlistService.status(listingId, userDetails.getUsername()));
    }
}
