package com.masterchefcuts.services;

import com.masterchefcuts.dto.ClaimResponse;
import com.masterchefcuts.dto.ListingResponse;
import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Claim;
import com.masterchefcuts.model.Cut;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.CutRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
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
class ClaimServiceTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private CutRepository cutRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ParticipantRepo participantRepo;
    @Mock private NotificationService notificationService;
    @Mock private ListingService listingService;
    @Mock private EmailService emailService;

    @InjectMocks private ClaimService claimService;

    private Participant farmer;
    private Participant buyer;
    private Listing listing;
    private Cut cut;

    @BeforeEach
    void setUp() {
        farmer = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm").shopName("Jane's Farm")
                .role(Role.FARMER).email("jane@farm.com").password("pass")
                .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        buyer = Participant.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .role(Role.BUYER).email("bob@buyer.com").password("pass")
                .street("2 Main St").city("Town").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        cut = Cut.builder().id(10L).label("Ribeye").claimed(false).build();

        listing = Listing.builder()
                .id(1L).farmer(farmer).animalType(AnimalType.BEEF).breed("Angus")
                .weightLbs(500).pricePerLb(10.0).zipCode("12345")
                .status(ListingStatus.ACTIVE).postedAt(LocalDateTime.now()).build();
        listing.getCuts().add(cut);
        cut.setListing(listing);
    }

    // ── claimCut ──────────────────────────────────────────────────────────────

    @Test
    void claimCut_success_savesCutAndClaim() {
        ListingResponse mockResponse = ListingResponse.builder().id(1L).build();

        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(cutRepository.findById(10L)).thenReturn(Optional.of(cut));
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));
        when(cutRepository.countByListingId(1L)).thenReturn(1L);
        when(cutRepository.countByListingIdAndClaimedTrue(1L)).thenReturn(0L);
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(listingService.toDto(any(Listing.class))).thenReturn(mockResponse);

        ListingResponse result = claimService.claimCut(1L, 10L, "buyer-1");

        assertThat(result).isNotNull();
        assertThat(cut.isClaimed()).isTrue();
        assertThat(cut.getClaimedBy()).isEqualTo(buyer);
        verify(cutRepository).save(cut);
        verify(claimRepository).save(any(Claim.class));
        verify(notificationService, times(2)).send(any(), any(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void claimCut_fullyClaimedWhenAllCutsClaimedAfterClaim() {
        ListingResponse mockResponse = ListingResponse.builder().id(1L).build();
        Claim existingClaim = Claim.builder().buyer(buyer).listing(listing).cut(cut).build();

        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(cutRepository.findById(10L)).thenReturn(Optional.of(cut));
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));
        when(cutRepository.countByListingId(1L)).thenReturn(1L);
        when(cutRepository.countByListingIdAndClaimedTrue(1L)).thenReturn(1L);
        when(claimRepository.findByListingIdOrderByClaimedAtAsc(1L)).thenReturn(List.of(existingClaim));
        when(listingService.toDto(any(Listing.class))).thenReturn(mockResponse);

        claimService.claimCut(1L, 10L, "buyer-1");

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.FULLY_CLAIMED);
        verify(listingRepository).save(listing);
        verify(emailService).sendPoolFullToFarmer(eq(farmer), eq(listing));
        verify(emailService).sendPoolFullToBuyers(anyList(), eq(listing));
    }

    @Test
    void claimCut_throwsWhenListingNotActive() {
        listing.setStatus(ListingStatus.FULLY_CLAIMED);
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> claimService.claimCut(1L, 10L, "buyer-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no longer accepting claims");
    }

    @Test
    void claimCut_throwsWhenCutAlreadyClaimed() {
        cut.setClaimed(true);
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(cutRepository.findById(10L)).thenReturn(Optional.of(cut));

        assertThatThrownBy(() -> claimService.claimCut(1L, 10L, "buyer-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already claimed");
    }

    @Test
    void claimCut_throwsWhenCutBelongsToDifferentListing() {
        Listing otherListing = Listing.builder().id(99L).farmer(farmer)
                .animalType(AnimalType.BEEF).breed("Other").zipCode("12345")
                .status(ListingStatus.ACTIVE).postedAt(LocalDateTime.now()).build();
        cut.setListing(otherListing);

        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(cutRepository.findById(10L)).thenReturn(Optional.of(cut));

        assertThatThrownBy(() -> claimService.claimCut(1L, 10L, "buyer-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void claimCut_throwsWhenBuyerNotFound() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(cutRepository.findById(10L)).thenReturn(Optional.of(cut));
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.claimCut(1L, 10L, "buyer-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Buyer not found");
    }

    @Test
    void claimCut_throwsWhenListingNotFound() {
        when(listingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.claimCut(99L, 10L, "buyer-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Listing not found");
    }

    @Test
    void claimCut_throwsWhenCutNotFound() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(cutRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.claimCut(1L, 99L, "buyer-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cut not found");
    }

    // ── getClaimsForBuyer ─────────────────────────────────────────────────────

    @Test
    void getClaimsForBuyer_returnsClaims() {
        Claim claim = Claim.builder().buyer(buyer).listing(listing).cut(cut).build();
        when(claimRepository.findByBuyerIdOrderByClaimedAtDesc("buyer-1")).thenReturn(List.of(claim));

        List<Claim> result = claimService.getClaimsForBuyer("buyer-1");

        assertThat(result).hasSize(1);
    }

    // ── getClaimResponsesForBuyer ─────────────────────────────────────────────

    @Test
    void getClaimResponsesForBuyer_mapsToDtoCorrectly() {
        Claim claim = Claim.builder().id(1L).buyer(buyer).listing(listing).cut(cut)
                .claimedAt(LocalDateTime.now()).build();
        when(claimRepository.findClaimSummariesByBuyerId("buyer-1")).thenReturn(List.of(claim));

        List<ClaimResponse> result = claimService.getClaimResponsesForBuyer("buyer-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCutLabel()).isEqualTo("Ribeye");
        assertThat(result.get(0).getListingId()).isEqualTo(1L);
    }

    // ── getClaimsForListing ───────────────────────────────────────────────────

    @Test
    void getClaimsForListing_returnsClaims() {
        Claim claim = Claim.builder().buyer(buyer).listing(listing).cut(cut).build();
        when(claimRepository.findByListingIdOrderByClaimedAtAsc(1L)).thenReturn(List.of(claim));

        List<Claim> result = claimService.getClaimsForListing(1L);

        assertThat(result).hasSize(1);
    }
}
