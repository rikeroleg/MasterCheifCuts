package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.ConnectOnboardingResponse;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.services.StripeConnectService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/connect")
@RequiredArgsConstructor
public class ConnectController {

    private final StripeConnectService stripeConnectService;
    private final ParticipantRepo participantRepo;

    /**
     * POST /api/connect/onboard
     * Creates (or refreshes) a Stripe Express onboarding link for the authenticated farmer.
     */
    @PreAuthorize("hasRole('FARMER')")
    @PostMapping("/onboard")
    public ResponseEntity<ConnectOnboardingResponse> onboard(
            @AuthenticationPrincipal String farmerId) throws StripeException {
        String url = stripeConnectService.createOnboardingLink(farmerId);
        return ResponseEntity.ok(new ConnectOnboardingResponse(url));
    }

    /**
     * GET /api/connect/dashboard
     * Returns a Stripe Express dashboard login link so the farmer can view their payouts.
     */
    @PreAuthorize("hasRole('FARMER')")
    @GetMapping("/dashboard")
    public ResponseEntity<ConnectOnboardingResponse> dashboard(
            @AuthenticationPrincipal String farmerId) throws StripeException {
        String url = stripeConnectService.createDashboardLink(farmerId);
        return ResponseEntity.ok(new ConnectOnboardingResponse(url));
    }

    /**
     * GET /api/connect/status
     * Returns the current Connect onboarding status for the authenticated farmer.
     */
    @PreAuthorize("hasRole('FARMER')")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(
            @AuthenticationPrincipal String farmerId) {
        Participant farmer = participantRepo.findById(farmerId)
                .orElseThrow(() -> new IllegalArgumentException("Farmer not found"));

        return ResponseEntity.ok(Map.of(
                "stripeAccountId",          farmer.getStripeAccountId() != null ? farmer.getStripeAccountId() : "",
                "stripeOnboardingComplete", Boolean.TRUE.equals(farmer.getStripeOnboardingComplete())
        ));
    }
}
