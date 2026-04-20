package com.masterchefcuts.services;

import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.model.Claim;
import com.masterchefcuts.model.Dispute;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.DisputeRepository;
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
class DisputeServiceTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private ClaimRepository claimRepository;
    @Mock private ParticipantRepo participantRepo;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;

    @InjectMocks private DisputeService disputeService;

    private Participant buyer;
    private Participant farmer;
    private Listing listing;
    private Claim claim;

    @BeforeEach
    void setUp() {
        buyer = Participant.builder()
                .id("buyer-1").firstName("Alice").lastName("Smith").build();

        farmer = Participant.builder()
                .id("farmer-1").firstName("Bob").lastName("Farm").build();

        listing = Listing.builder()
                .id(10L).farmer(farmer).build();

        claim = Claim.builder()
                .id(5L).buyer(buyer).listing(listing).build();
    }

    // ── createDispute ─────────────────────────────────────────────────────────

    @Test
    void createDispute_success_savesAndReturns() {
        when(disputeRepository.existsByClaimIdAndStatus(5L, "OPEN")).thenReturn(false);
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(farmer));
        when(claimRepository.findById(5L)).thenReturn(Optional.of(claim));

        Dispute saved = Dispute.builder()
                .id(1L).buyerId("buyer-1").buyerName("Alice Smith")
                .farmerId("farmer-1").farmerName("Bob Farm")
                .claimId(5L).listingId(10L)
                .type("QUALITY").description("Bad cuts")
                .status("OPEN").build();
        when(disputeRepository.save(any(Dispute.class))).thenReturn(saved);

        Dispute result = disputeService.createDispute("buyer-1", 5L, 10L, "QUALITY", "Bad cuts");

        assertThat(result.getBuyerId()).isEqualTo("buyer-1");
        assertThat(result.getBuyerName()).isEqualTo("Alice Smith");
        assertThat(result.getFarmerId()).isEqualTo("farmer-1");
        assertThat(result.getStatus()).isEqualTo("OPEN");
        verify(disputeRepository).save(any(Dispute.class));
    }

    @Test
    void createDispute_notifiesBuyerAndFarmer() {
        when(disputeRepository.existsByClaimIdAndStatus(5L, "OPEN")).thenReturn(false);
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(farmer));
        when(claimRepository.findById(5L)).thenReturn(Optional.of(claim));

        Dispute saved = Dispute.builder().id(1L).buyerId("buyer-1").farmerId("farmer-1")
                .claimId(5L).listingId(10L).status("OPEN").build();
        when(disputeRepository.save(any(Dispute.class))).thenReturn(saved);

        disputeService.createDispute("buyer-1", 5L, 10L, "QUALITY", "Bad cuts");

        verify(notificationService, times(1)).send(eq(buyer), eq(NotificationType.DISPUTE_OPENED), any(), any(), any(), eq(10L));
        verify(notificationService, times(1)).send(eq(farmer), eq(NotificationType.DISPUTE_OPENED), any(), any(), any(), eq(10L));
        verify(emailService, times(1)).sendDisputeOpened(eq(buyer), eq(1L), eq(10L), eq(true));
        verify(emailService, times(1)).sendDisputeOpened(eq(farmer), eq(1L), eq(10L), eq(false));
    }

    @Test
    void createDispute_duplicateOpenDispute_throwsIllegalState() {
        when(disputeRepository.existsByClaimIdAndStatus(5L, "OPEN")).thenReturn(true);

        assertThatThrownBy(() ->
                disputeService.createDispute("buyer-1", 5L, 10L, "QUALITY", "Bad cuts"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("open dispute");

        verify(disputeRepository, never()).save(any());
    }

    // ── getAllDisputes ─────────────────────────────────────────────────────────

    @Test
    void getAllDisputes_returnsSortedList() {
        Dispute d1 = Dispute.builder().id(1L).status("OPEN").createdAt(LocalDateTime.now()).build();
        Dispute d2 = Dispute.builder().id(2L).status("RESOLVED").createdAt(LocalDateTime.now().minusDays(1)).build();
        when(disputeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(d1, d2));

        List<Dispute> result = disputeService.getAllDisputes();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    // ── resolveDispute ────────────────────────────────────────────────────────

    @Test
    void resolveDispute_success_setsStatusAndResolution() {
        Dispute dispute = Dispute.builder().id(1L).status("OPEN").build();
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        Dispute result = disputeService.resolveDispute(1L, "Refund issued");

        assertThat(result.getStatus()).isEqualTo("RESOLVED");
        assertThat(result.getResolution()).isEqualTo("Refund issued");
        assertThat(result.getResolvedAt()).isNotNull();
    }

    @Test
    void resolveDispute_notFound_throwsIllegalArgument() {
        when(disputeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeService.resolveDispute(99L, "Whatever"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
