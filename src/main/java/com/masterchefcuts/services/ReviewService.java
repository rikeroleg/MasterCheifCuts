package com.masterchefcuts.services;

import com.masterchefcuts.dto.ReviewRequest;
import com.masterchefcuts.dto.ReviewResponse;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.model.Review;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.repositories.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ListingRepository listingRepository;
    private final ParticipantRepo participantRepo;
    private final ClaimRepository claimRepository;

    public ReviewResponse createReview(String buyerId, ReviewRequest req) {
        Listing listing = listingRepository.findById(req.getListingId())
                .orElseThrow(() -> new RuntimeException("Listing not found"));

        boolean hasClaim = claimRepository.findByBuyerIdOrderByClaimedAtDesc(buyerId).stream()
                .anyMatch(c -> c.getListing().getId().equals(req.getListingId()));
        if (!hasClaim)
            throw new RuntimeException("You must have claimed a cut on this listing to leave a review");

        if (reviewRepository.existsByBuyerIdAndListingId(buyerId, req.getListingId()))
            throw new RuntimeException("You have already reviewed this listing");

        Participant buyer = participantRepo.findById(buyerId)
                .orElseThrow(() -> new RuntimeException("Buyer not found"));

        Review review = reviewRepository.save(Review.builder()
                .buyer(buyer)
                .listing(listing)
                .rating(req.getRating())
                .comment(stripHtml(req.getComment()))
                .build());

        return toDto(review);
    }

    public List<ReviewResponse> getReviewsForListing(Long listingId) {
        return reviewRepository.findByListingIdOrderByCreatedAtDesc(listingId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<ReviewResponse> getReviewsForFarmer(String farmerId) {
        return reviewRepository.findByListingFarmerIdOrderByCreatedAtDesc(farmerId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public boolean hasReviewed(String buyerId, Long listingId) {
        return reviewRepository.existsByBuyerIdAndListingId(buyerId, listingId);
    }

    /** Returns up to 6 high-quality reviews for the homepage testimonials section. */
    public List<ReviewResponse> getFeaturedReviews() {
        return reviewRepository.findFeatured(PageRequest.of(0, 6))
                .stream()
                .map(r -> toDto(r, true))
                .collect(Collectors.toList());
    }

    private String stripHtml(String input) {
        if (input == null) return "";
        return input.replaceAll("<[^>]*>", "").trim();
    }

    private ReviewResponse toDto(Review r) {
        return toDto(r, false);
    }

    private ReviewResponse toDto(Review r, boolean includeListing) {
        ReviewResponse.ReviewResponseBuilder builder = ReviewResponse.builder()
                .id(r.getId())
                .listingId(r.getListing().getId())
                .buyerName(r.getBuyer().getFirstName() + " " + r.getBuyer().getLastName().charAt(0) + ".")
                .rating(r.getRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt());
        if (includeListing) {
            Listing l = r.getListing();
            builder.animalType(l.getAnimalType() != null ? l.getAnimalType().toString() : null)
                   .farmerShopName(l.getFarmer() != null
                           ? (l.getFarmer().getShopName() != null ? l.getFarmer().getShopName() : l.getFarmer().getFirstName())
                           : null);
        }
        return builder.build();
    }
}
