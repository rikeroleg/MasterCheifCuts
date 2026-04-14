package com.masterchefcuts.services;

import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.model.WaitlistEntry;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.repositories.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock private WaitlistRepository waitlistRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ParticipantRepo participantRepo;

    @InjectMocks private WaitlistService waitlistService;

    private Participant buyer;
    private Participant farmer;
    private Listing listing;
    private WaitlistEntry entry;

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

        entry = WaitlistEntry.builder().id(1L).buyer(buyer).listing(listing).build();
    }

    // ── join ──────────────────────────────────────────────────────────────────

    @Test
    void join_alreadyOnWaitlist_returnsEarlyWithMessage() {
        when(waitlistRepository.existsByBuyerIdAndListingId("buyer-1", 1L)).thenReturn(true);

        Map<String, Object> result = waitlistService.join(1L, "buyer-1");

        assertThat(result.get("onWaitlist")).isEqualTo(true);
        assertThat(result.get("message")).isEqualTo("Already on waitlist");
        verify(waitlistRepository, never()).save(any());
    }

    @Test
    void join_newEntry_savesAndReturnsPosition() {
        when(waitlistRepository.existsByBuyerIdAndListingId("buyer-1", 1L)).thenReturn(false);
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));
        when(waitlistRepository.findByListingIdOrderByJoinedAtAsc(1L)).thenReturn(List.of(entry));

        Map<String, Object> result = waitlistService.join(1L, "buyer-1");

        assertThat(result.get("onWaitlist")).isEqualTo(true);
        verify(waitlistRepository).save(any(WaitlistEntry.class));
    }

    @Test
    void join_throwsWhenListingNotFound() {
        when(waitlistRepository.existsByBuyerIdAndListingId("buyer-1", 99L)).thenReturn(false);
        when(listingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> waitlistService.join(99L, "buyer-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Listing not found");
    }

    @Test
    void join_throwsWhenBuyerNotFound() {
        when(waitlistRepository.existsByBuyerIdAndListingId("buyer-1", 1L)).thenReturn(false);
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> waitlistService.join(1L, "buyer-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Buyer not found");
    }

    // ── leave ─────────────────────────────────────────────────────────────────

    @Test
    void leave_deletesEntryForBuyerAndListing() {
        waitlistService.leave(1L, "buyer-1");

        verify(waitlistRepository).deleteByBuyerIdAndListingId("buyer-1", 1L);
    }

    // ── status ────────────────────────────────────────────────────────────────

    @Test
    void status_onWaitlist_returnsPositionAndTotal() {
        WaitlistEntry other = WaitlistEntry.builder().id(2L)
                .buyer(Participant.builder().id("other-buyer").firstName("O").lastName("B")
                        .role(Role.BUYER).email("o@b.com").password("p")
                        .street("x").city("x").state("TX").zipCode("x").status("ACTIVE").build())
                .listing(listing).build();

        when(waitlistRepository.existsByBuyerIdAndListingId("buyer-1", 1L)).thenReturn(true);
        when(waitlistRepository.findByListingIdOrderByJoinedAtAsc(1L))
                .thenReturn(List.of(other, entry));

        Map<String, Object> result = waitlistService.status(1L, "buyer-1");

        assertThat(result.get("onWaitlist")).isEqualTo(true);
        assertThat(result.get("total")).isEqualTo(2L);
        assertThat(result).containsKey("position");
    }

    @Test
    void status_notOnWaitlist_returnsOnWaitlistFalse() {
        when(waitlistRepository.existsByBuyerIdAndListingId("buyer-1", 1L)).thenReturn(false);
        when(waitlistRepository.findByListingIdOrderByJoinedAtAsc(1L)).thenReturn(List.of());

        Map<String, Object> result = waitlistService.status(1L, "buyer-1");

        assertThat(result.get("onWaitlist")).isEqualTo(false);
        assertThat(result.get("total")).isEqualTo(0L);
        assertThat(result).doesNotContainKey("position");
    }
}
