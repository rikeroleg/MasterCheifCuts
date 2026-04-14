package com.masterchefcuts.services;

import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.model.Referral;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.repositories.ReferralRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock private ReferralRepository referralRepository;
    @Mock private ParticipantRepo participantRepo;

    @InjectMocks private ReferralService referralService;

    private Participant referrer;
    private Participant referred;

    @BeforeEach
    void setUp() {
        referrer = Participant.builder()
                .id("referrer-1").firstName("Alice").lastName("Refer")
                .role(Role.BUYER).email("alice@test.com").password("pass")
                .street("1 St").city("City").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        referred = Participant.builder()
                .id("referred-1").firstName("Bob").lastName("New")
                .role(Role.BUYER).email("bob@test.com").password("pass")
                .street("2 St").city("City").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();
    }

    // ── getMyStats ────────────────────────────────────────────────────────────

    @Test
    void getMyStats_noReferrals_returnsZeros() {
        when(referralRepository.findByReferrerId("referrer-1")).thenReturn(List.of());

        Map<String, Object> stats = referralService.getMyStats("referrer-1");

        assertThat(stats.get("totalReferrals")).isEqualTo(0);
        assertThat(stats.get("activeReferrals")).isEqualTo(0);
    }

    @Test
    void getMyStats_withActiveReferral_countsCorrectly() {
        Referral referral = Referral.builder()
                .id(1L).referrerId("referrer-1").referredId("referred-1").build();

        when(referralRepository.findByReferrerId("referrer-1")).thenReturn(List.of(referral));
        when(participantRepo.findById("referred-1")).thenReturn(Optional.of(referred));

        Map<String, Object> stats = referralService.getMyStats("referrer-1");

        assertThat(stats.get("totalReferrals")).isEqualTo(1);
        assertThat(stats.get("activeReferrals")).isEqualTo(1);
    }

    @Test
    void getMyStats_inactiveReferral_countedInTotalButNotActive() {
        referred.setStatus("INACTIVE");
        Referral referral = Referral.builder()
                .id(1L).referrerId("referrer-1").referredId("referred-1").build();

        when(referralRepository.findByReferrerId("referrer-1")).thenReturn(List.of(referral));
        when(participantRepo.findById("referred-1")).thenReturn(Optional.of(referred));

        Map<String, Object> stats = referralService.getMyStats("referrer-1");

        assertThat(stats.get("totalReferrals")).isEqualTo(1);
        assertThat(stats.get("activeReferrals")).isEqualTo(0);
    }

    // ── recordReferral ────────────────────────────────────────────────────────

    @Test
    void recordReferral_newReferral_saves() {
        when(referralRepository.existsByReferredId("referred-1")).thenReturn(false);
        when(participantRepo.existsById("referrer-1")).thenReturn(true);

        referralService.recordReferral("referrer-1", "referred-1");

        verify(referralRepository).save(any(Referral.class));
    }

    @Test
    void recordReferral_duplicateReferredId_doesNotSave() {
        when(referralRepository.existsByReferredId("referred-1")).thenReturn(true);

        referralService.recordReferral("referrer-1", "referred-1");

        verify(referralRepository, never()).save(any());
    }

    @Test
    void recordReferral_unknownReferrer_doesNotSave() {
        when(referralRepository.existsByReferredId("referred-1")).thenReturn(false);
        when(participantRepo.existsById("bad-referrer")).thenReturn(false);

        referralService.recordReferral("bad-referrer", "referred-1");

        verify(referralRepository, never()).save(any());
    }

    @Test
    void recordReferral_savesCorrectReferrerAndReferredIds() {
        when(referralRepository.existsByReferredId("referred-1")).thenReturn(false);
        when(participantRepo.existsById("referrer-1")).thenReturn(true);

        referralService.recordReferral("referrer-1", "referred-1");

        verify(referralRepository).save(argThat(r ->
                "referrer-1".equals(r.getReferrerId()) &&
                "referred-1".equals(r.getReferredId())));
    }
}
