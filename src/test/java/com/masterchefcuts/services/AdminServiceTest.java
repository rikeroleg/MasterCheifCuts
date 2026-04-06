package com.masterchefcuts.services;

import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ClaimRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private ParticipantRepo participantRepo;
    @Mock private ListingRepository listingRepository;
    @Mock private ClaimRepository claimRepository;

    @InjectMocks private AdminService adminService;

    private Participant buyer;
    private Participant farmer;
    private Participant pendingFarmer;
    private Listing listing;

    @BeforeEach
    void setUp() {
        buyer = Participant.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .role(Role.BUYER).email("bob@example.com").password("pass")
                .street("1 St").city("City").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        farmer = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm")
                .role(Role.FARMER).email("jane@farm.com").password("pass")
                .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        pendingFarmer = Participant.builder()
                .id("farmer-2").firstName("New").lastName("Farmer")
                .role(Role.FARMER).email("new@farm.com").password("pass")
                .street("2 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(false).build();

        listing = Listing.builder()
                .id(1L).farmer(farmer).animalType(AnimalType.BEEF).breed("Angus")
                .weightLbs(500).pricePerLb(10.0).zipCode("12345")
                .status(ListingStatus.ACTIVE).postedAt(LocalDateTime.now()).build();
    }

    // ── getAllUsers ───────────────────────────────────────────────────────────

    @Test
    void getAllUsers_returnsAllParticipants() {
        when(participantRepo.findAll()).thenReturn(List.of(buyer, farmer, pendingFarmer));

        List<Participant> result = adminService.getAllUsers();

        assertThat(result).hasSize(3);
    }

    // ── setApproved ───────────────────────────────────────────────────────────

    @Test
    void setApproved_approvesUser() {
        when(participantRepo.findById("farmer-2")).thenReturn(Optional.of(pendingFarmer));
        when(participantRepo.save(pendingFarmer)).thenReturn(pendingFarmer);

        Participant result = adminService.setApproved("farmer-2", true);

        assertThat(result.isApproved()).isTrue();
        verify(participantRepo).save(pendingFarmer);
    }

    @Test
    void setApproved_revokesApproval() {
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(farmer));
        when(participantRepo.save(farmer)).thenReturn(farmer);

        Participant result = adminService.setApproved("farmer-1", false);

        assertThat(result.isApproved()).isFalse();
    }

    @Test
    void setApproved_throwsWhenUserNotFound() {
        when(participantRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.setApproved("missing", true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    // ── deleteListing ─────────────────────────────────────────────────────────

    @Test
    void deleteListing_callsRepositoryDeleteById() {
        adminService.deleteListing(1L);

        verify(listingRepository).deleteById(1L);
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    void getStats_returnsAggregatedCounts() {
        when(participantRepo.count()).thenReturn(3L);
        when(listingRepository.count()).thenReturn(2L);
        when(claimRepository.count()).thenReturn(5L);
        when(participantRepo.findAll()).thenReturn(List.of(buyer, farmer, pendingFarmer));

        Map<String, Object> stats = adminService.getStats();

        assertThat(stats.get("totalUsers")).isEqualTo(3L);
        assertThat(stats.get("totalListings")).isEqualTo(2L);
        assertThat(stats.get("totalClaims")).isEqualTo(5L);
        assertThat(stats.get("pendingFarmers")).isEqualTo(1L);
    }

    @Test
    void getStats_pendingFarmersCountsOnlyUnapprovedFarmers() {
        when(participantRepo.count()).thenReturn(1L);
        when(listingRepository.count()).thenReturn(0L);
        when(claimRepository.count()).thenReturn(0L);
        when(participantRepo.findAll()).thenReturn(List.of(buyer));

        Map<String, Object> stats = adminService.getStats();

        assertThat(stats.get("pendingFarmers")).isEqualTo(0L);
    }
}
