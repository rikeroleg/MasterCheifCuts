package com.masterchefcuts.services;

import com.masterchefcuts.dto.ReviewRequest;
import com.masterchefcuts.dto.ReviewResponse;
import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Claim;
import com.masterchefcuts.model.Cut;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.model.Review;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.repositories.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ParticipantRepo participantRepo;
    @Mock private ClaimRepository claimRepository;

    @InjectMocks private ReviewService reviewService;

    private Participant buyer;
    private Participant farmer;
    private Listing listing;
    private Cut cut;
    private Claim claim;

    @BeforeEach
    void setUp() {
        farmer = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm")
                .role(Role.FARMER).email("jane@farm.com").password("pass")
                .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        buyer = Participant.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .role(Role.BUYER).email("bob@buyer.com").password("pass")
                .street("2 Main St").city("Town").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        cut = Cut.builder().id(10L).label("Ribeye").claimed(true).claimedBy(buyer).build();

        listing = Listing.builder()
                .id(1L).farmer(farmer).animalType(AnimalType.BEEF).breed("Angus")
                .weightLbs(500).pricePerLb(10.0).zipCode("12345")
                .status(ListingStatus.FULLY_CLAIMED).postedAt(LocalDateTime.now()).build();
        listing.getCuts().add(cut);
        cut.setListing(listing);

        claim = Claim.builder().id(1L).buyer(buyer).listing(listing).cut(cut).build();
    }

    // ── createReview ──────────────────────────────────────────────────────────

    @Test
    void createReview_success_savesAndReturnsDto() {
        ReviewRequest req = new ReviewRequest();
        req.setListingId(1L);
        req.setRating(5);
        req.setComment("Amazing beef!");

        Review savedReview = Review.builder()
                .id(1L).buyer(buyer).listing(listing)
                .rating(5).comment("Amazing beef!").createdAt(LocalDateTime.now()).build();

        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(claimRepository.findByBuyerIdOrderByClaimedAtDesc("buyer-1")).thenReturn(List.of(claim));
        when(reviewRepository.existsByBuyerIdAndListingId("buyer-1", 1L)).thenReturn(false);
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));
        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);

        ReviewResponse response = reviewService.createReview("buyer-1", req);

        assertThat(response).isNotNull();
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getComment()).isEqualTo("Amazing beef!");
        assertThat(response.getBuyerName()).isEqualTo("Bob B.");
    }

    @Test
    void createReview_throwsWhenBuyerHasNoClaim() {
        ReviewRequest req = new ReviewRequest();
        req.setListingId(1L);
        req.setRating(4);

        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(claimRepository.findByBuyerIdOrderByClaimedAtDesc("buyer-1")).thenReturn(List.of());

        assertThatThrownBy(() -> reviewService.createReview("buyer-1", req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("must have claimed a cut");
    }

    @Test
    void createReview_throwsOnDuplicateReview() {
        ReviewRequest req = new ReviewRequest();
        req.setListingId(1L);
        req.setRating(3);

        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(claimRepository.findByBuyerIdOrderByClaimedAtDesc("buyer-1")).thenReturn(List.of(claim));
        when(reviewRepository.existsByBuyerIdAndListingId("buyer-1", 1L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview("buyer-1", req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    void createReview_throwsWhenListingNotFound() {
        ReviewRequest req = new ReviewRequest();
        req.setListingId(99L);
        req.setRating(5);

        when(listingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview("buyer-1", req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Listing not found");
    }

    @Test
    void createReview_throwsWhenBuyerNotFound() {
        ReviewRequest req = new ReviewRequest();
        req.setListingId(1L);
        req.setRating(4);

        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(claimRepository.findByBuyerIdOrderByClaimedAtDesc("buyer-1")).thenReturn(List.of(claim));
        when(reviewRepository.existsByBuyerIdAndListingId("buyer-1", 1L)).thenReturn(false);
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview("buyer-1", req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Buyer not found");
    }

    // ── getReviewsForListing ──────────────────────────────────────────────────

    @Test
    void getReviewsForListing_returnsListOrderedByDate() {
        Review review = Review.builder()
                .id(1L).buyer(buyer).listing(listing)
                .rating(4).comment("Good").createdAt(LocalDateTime.now()).build();

        when(reviewRepository.findByListingIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(review));

        List<ReviewResponse> result = reviewService.getReviewsForListing(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRating()).isEqualTo(4);
        assertThat(result.get(0).getBuyerName()).isEqualTo("Bob B.");
    }

    @Test
    void getReviewsForListing_returnsEmptyListWhenNone() {
        when(reviewRepository.findByListingIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        List<ReviewResponse> result = reviewService.getReviewsForListing(1L);

        assertThat(result).isEmpty();
    }
}
