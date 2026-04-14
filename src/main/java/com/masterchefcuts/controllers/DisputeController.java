package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.DisputeRequest;
import com.masterchefcuts.model.Dispute;
import com.masterchefcuts.services.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    /**
     * Buyer submits a dispute for a claim.
     */
    @PreAuthorize("hasRole('BUYER')")
    @PostMapping("/api/disputes")
    public ResponseEntity<Dispute> createDispute(
            @AuthenticationPrincipal String buyerId,
            @Valid @RequestBody DisputeRequest request) {
        if (buyerId == null) return ResponseEntity.status(401).build();
        try {
            Dispute dispute = disputeService.createDispute(
                    buyerId,
                    request.getClaimId(),
                    request.getListingId(),
                    request.getType(),
                    request.getDescription());
            return ResponseEntity.ok(dispute);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    /**
     * Admin retrieves all disputes.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/disputes")
    public ResponseEntity<List<Dispute>> getAllDisputes() {
        return ResponseEntity.ok(disputeService.getAllDisputes());
    }

    /**
     * Admin resolves a dispute.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/admin/disputes/{id}/resolve")
    public ResponseEntity<Dispute> resolveDispute(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String resolution = body.get("resolution");
        if (resolution == null || resolution.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(disputeService.resolveDispute(id, resolution));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
