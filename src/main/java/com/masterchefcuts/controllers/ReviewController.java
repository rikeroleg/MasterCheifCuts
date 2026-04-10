package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.ReviewRequest;
import com.masterchefcuts.dto.ReviewResponse;
import com.masterchefcuts.services.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/api/listings/{listingId}/reviews")
    public ResponseEntity<List<ReviewResponse>> getReviews(@PathVariable Long listingId) {
        return ResponseEntity.ok(reviewService.getReviewsForListing(listingId));
    }

    @GetMapping("/api/reviews/farmer/{farmerId}")
    public ResponseEntity<List<ReviewResponse>> getFarmerReviews(@PathVariable String farmerId) {
        return ResponseEntity.ok(reviewService.getReviewsForFarmer(farmerId));
    }

    @GetMapping("/api/reviews/has-reviewed")
    public ResponseEntity<Boolean> hasReviewed(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long listingId) {
        if (userDetails == null) return ResponseEntity.ok(false);
        return ResponseEntity.ok(reviewService.hasReviewed(userDetails.getUsername(), listingId));
    }

    @PreAuthorize("hasRole('BUYER')")
    @PostMapping("/api/reviews")
    public ResponseEntity<ReviewResponse> createReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReviewRequest req) {
        return ResponseEntity.ok(reviewService.createReview(userDetails.getUsername(), req));
    }
}
