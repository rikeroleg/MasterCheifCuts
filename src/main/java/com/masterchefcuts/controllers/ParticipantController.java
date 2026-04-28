package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.AuthResponse;
import com.masterchefcuts.enums.EmailPreference;
import com.masterchefcuts.enums.NotificationPreference;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.model.Review;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.repositories.ReviewRepository;
import com.masterchefcuts.services.AuthService;
import com.masterchefcuts.services.OrderService;
import com.masterchefcuts.services.ParticipantService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/participants")
@RequiredArgsConstructor
public class ParticipantController {

    @Autowired
    private ParticipantService participantService;

    private final ParticipantRepo participantRepo;
    private final AuthService authService;
    private final ListingRepository listingRepository;
    private final OrderService orderService;
    private final ReviewRepository reviewRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/add")
    public String add(@RequestBody Participant participant) {
        participantService.addParticipant(participant);
        return "Participant added successfully";
    }

    @PatchMapping("/me/notification-preference")
    public ResponseEntity<AuthResponse> updateNotificationPreference(
            @AuthenticationPrincipal String participantId,
            @RequestBody Map<String, String> body) {
        String raw = body.get("preference");
        if (raw == null || raw.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        NotificationPreference pref;
        try {
            pref = NotificationPreference.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        Participant p = participantRepo.findById(participantId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));
        p.setNotificationPreference(pref);
        participantRepo.save(p);
        return ResponseEntity.ok(authService.getMe(participantId));
    }

    @PreAuthorize("hasRole('FARMER')")
    @GetMapping("/me/analytics")
    public ResponseEntity<Map<String, Object>> getMyAnalytics(@AuthenticationPrincipal String farmerId) {
        var listings = listingRepository.findByFarmerIdOrderByPostedAtDesc(farmerId);

        int totalListings = listings.size();
        long activeListings = listings.stream()
                .filter(l -> l.getStatus() == ListingStatus.ACTIVE)
                .count();
        long totalCutsClaimed = listings.stream()
                .flatMap(l -> l.getCuts().stream())
                .filter(c -> c.isClaimed())
                .count();

        double totalRevenue = orderService.getFarmerOrders(farmerId).stream()
                .filter(o -> "COMPLETED".equalsIgnoreCase(o.getStatus()))
                .mapToDouble(o -> o.getAmountCents() != null ? o.getAmountCents() / 100.0 : o.getTotalAmount())
                .sum();

        List<Review> reviews = reviewRepository.findByListingFarmerIdOrderByCreatedAtDesc(farmerId);
        double averageRating = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        Map<String, Object> result = new HashMap<>();
        result.put("totalListings", totalListings);
        result.put("activeListings", activeListings);
        result.put("totalCutsClaimed", totalCutsClaimed);
        result.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
        result.put("averageRating", Math.round(averageRating * 10.0) / 10.0);
        result.put("totalReviews", reviews.size());
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/me/email-preference")
    public ResponseEntity<AuthResponse> updateEmailPreference(
            @AuthenticationPrincipal String participantId,
            @RequestBody Map<String, String> body) {
        String raw = body.get("emailPreference");
        if (raw == null || raw.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        EmailPreference pref;
        try {
            pref = EmailPreference.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        Participant p = participantRepo.findById(participantId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));
        p.setEmailPreference(pref);
        participantRepo.save(p);
        return ResponseEntity.ok(authService.getMe(participantId));
    }

    /**
     * Public farmer profile — exposes only non-sensitive info for storefront display.
     * Accessible without authentication so guests can browse farmer storefronts.
     */
    @GetMapping("/{id}/public")
    public ResponseEntity<Map<String, Object>> getPublicProfile(@PathVariable String id) {
        return participantRepo.findById(id)
                .filter(p -> Role.FARMER.equals(p.getRole()))
                .map(p -> {
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("id", p.getId());
                    profile.put("name", p.getFirstName() + (p.getLastName() != null ? " " + p.getLastName() : ""));
                    profile.put("shopName", p.getShopName());
                    profile.put("bio", p.getBio());
                    profile.put("certifications", p.getCertifications());
                    profile.put("zipCode", p.getZipCode());
                    return ResponseEntity.ok(profile);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
