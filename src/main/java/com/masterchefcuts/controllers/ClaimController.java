package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.ClaimRequest;
import com.masterchefcuts.dto.ClaimResponse;
import com.masterchefcuts.dto.ListingResponse;
import com.masterchefcuts.services.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    @PostMapping("/api/listings/{listingId}/claims")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ListingResponse> claimCut(
            @PathVariable Long listingId,
            @AuthenticationPrincipal String buyerId,
            @Valid @RequestBody ClaimRequest req) {
        return ResponseEntity.ok(claimService.claimCut(listingId, req.getCutId(), buyerId));
    }

    @GetMapping("/api/listings/{listingId}/claims")
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<?> getClaimsForListing(@PathVariable Long listingId,
                                                  @AuthenticationPrincipal String farmerId) {
        return ResponseEntity.ok(claimService.getClaimsForListing(listingId));
    }

    @GetMapping("/api/claims/my")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<java.util.List<ClaimResponse>> getMyClaims(
            @AuthenticationPrincipal String buyerId) {
        return ResponseEntity.ok(claimService.getClaimResponsesForBuyer(buyerId));
    }
}
