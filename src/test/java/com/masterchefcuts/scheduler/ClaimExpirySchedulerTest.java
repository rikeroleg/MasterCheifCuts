package com.masterchefcuts.scheduler;

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
import com.masterchefcuts.services.NotificationService;
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
class ClaimExpirySchedulerTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private CutRepository cutRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private ClaimExpiryScheduler scheduler;

    private Participant farmer;
    private Participant buyer;
    private Listing listing;
    private Cut cut;
    private Claim expiredClaim;

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

        listing = Listing.builder()
                .id(1L).farmer(farmer).animalType(AnimalType.BEEF).breed("Angus")
                .weightLbs(500).pricePerLb(10.0).zipCode("12345")
                .status(ListingStatus.FULLY_CLAIMED).postedAt(LocalDateTime.now()).build();

        cut = Cut.builder()
                .id(1L).label("Ribeye").listing(listing)
                .claimed(true).claimedBy(buyer).claimedAt(LocalDateTime.now().minusHours(2)).build();

        expiredClaim = Claim.builder()
                .id(1L).buyer(buyer).listing(listing).cut(cut)
                .claimedAt(LocalDateTime.now().minusHours(2))
                .expiresAt(LocalDateTime.now().minusMinutes(10))
                .paid(false).build();
    }

    // ── expireUnpaidClaims ────────────────────────────────────────────────────

    @Test
    void expireUnpaidClaims_noExpiredClaims_doesNothing() {
        when(claimRepository.findByPaidFalseAndExpiresAtBefore(any())).thenReturn(List.of());

        scheduler.expireUnpaidClaims();

        verify(cutRepository, never()).save(any());
        verify(claimRepository, never()).delete(any());
        verify(notificationService, never()).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void expireUnpaidClaims_expiredClaim_releasesCutAndDeletesClaim() {
        when(claimRepository.findByPaidFalseAndExpiresAtBefore(any())).thenReturn(List.of(expiredClaim));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        scheduler.expireUnpaidClaims();

        // Cut should be released
        assertThat(cut.isClaimed()).isFalse();
        assertThat(cut.getClaimedBy()).isNull();
        assertThat(cut.getClaimedAt()).isNull();
        verify(cutRepository).save(cut);

        // Claim should be deleted
        verify(claimRepository).delete(expiredClaim);
    }

    @Test
    void expireUnpaidClaims_expiredClaim_notifiesBuyer() {
        when(claimRepository.findByPaidFalseAndExpiresAtBefore(any())).thenReturn(List.of(expiredClaim));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        scheduler.expireUnpaidClaims();

        verify(notificationService).send(
                eq(buyer),
                eq(NotificationType.CLAIM_EXPIRED),
                eq("⏰"),
                eq("Claim expired"),
                contains("Ribeye"),
                eq(listing.getId())
        );
    }

    @Test
    void expireUnpaidClaims_fullyClaimedListing_reopensToActive() {
        listing.setStatus(ListingStatus.FULLY_CLAIMED);
        listing.setFullyClaimedAt(LocalDateTime.now().minusHours(1));

        when(claimRepository.findByPaidFalseAndExpiresAtBefore(any())).thenReturn(List.of(expiredClaim));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        scheduler.expireUnpaidClaims();

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(listing.getFullyClaimedAt()).isNull();
        verify(listingRepository).save(listing);
    }

    @Test
    void expireUnpaidClaims_activeListing_doesNotChangeStatus() {
        listing.setStatus(ListingStatus.ACTIVE);

        when(claimRepository.findByPaidFalseAndExpiresAtBefore(any())).thenReturn(List.of(expiredClaim));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        scheduler.expireUnpaidClaims();

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void expireUnpaidClaims_multipleClaims_processesAll() {
        Cut cut2 = Cut.builder()
                .id(2L).label("Brisket").listing(listing)
                .claimed(true).claimedBy(buyer).claimedAt(LocalDateTime.now().minusHours(3)).build();

        Claim expiredClaim2 = Claim.builder()
                .id(2L).buyer(buyer).listing(listing).cut(cut2)
                .expiresAt(LocalDateTime.now().minusMinutes(5)).paid(false).build();

        when(claimRepository.findByPaidFalseAndExpiresAtBefore(any()))
                .thenReturn(List.of(expiredClaim, expiredClaim2));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        scheduler.expireUnpaidClaims();

        verify(cutRepository).save(cut);
        verify(cutRepository).save(cut2);
        verify(claimRepository).delete(expiredClaim);
        verify(claimRepository).delete(expiredClaim2);
    }
}
