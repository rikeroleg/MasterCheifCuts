package com.masterchefcuts.services;

import com.masterchefcuts.model.Referral;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.repositories.ReferralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private final ReferralRepository referralRepository;
    private final ParticipantRepo participantRepo;

    public Map<String, Object> getMyStats(String userId) {
        List<Referral> referrals = referralRepository.findByReferrerId(userId);
        long activeCount = referrals.stream()
                .map(r -> participantRepo.findById(r.getReferredId()).orElse(null))
                .filter(p -> p != null && "ACTIVE".equalsIgnoreCase(p.getStatus()))
                .count();
        return Map.of(
                "totalReferrals", referrals.size(),
                "activeReferrals", (int) activeCount
        );
    }

    @Transactional
    public void recordReferral(String referrerId, String referredId) {
        // Ignore if this referred user was already recorded (idempotent)
        if (referralRepository.existsByReferredId(referredId)) {
            return;
        }
        // Silently ignore if the referrer ID doesn't correspond to a known user
        if (!participantRepo.existsById(referrerId)) {
            return;
        }
        referralRepository.save(Referral.builder()
                .referrerId(referrerId)
                .referredId(referredId)
                .build());
    }
}
