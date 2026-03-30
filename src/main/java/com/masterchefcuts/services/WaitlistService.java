package com.masterchefcuts.services;

import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.model.WaitlistEntry;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.repositories.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final ListingRepository listingRepository;
    private final ParticipantRepo participantRepo;

    @Transactional
    public Map<String, Object> join(Long listingId, String buyerId) {
        if (waitlistRepository.existsByBuyerIdAndListingId(buyerId, listingId))
            return Map.of("onWaitlist", true, "message", "Already on waitlist");

        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new RuntimeException("Listing not found"));
        Participant buyer = participantRepo.findById(buyerId)
                .orElseThrow(() -> new RuntimeException("Buyer not found"));

        waitlistRepository.save(WaitlistEntry.builder().buyer(buyer).listing(listing).build());
        long position = waitlistRepository.findByListingIdOrderByJoinedAtAsc(listingId).stream()
                .filter(e -> e.getBuyer().getId().equals(buyerId)).count();
        return Map.of("onWaitlist", true, "position", position);
    }

    @Transactional
    public void leave(Long listingId, String buyerId) {
        waitlistRepository.deleteByBuyerIdAndListingId(buyerId, listingId);
    }

    public Map<String, Object> status(Long listingId, String buyerId) {
        boolean onWaitlist = waitlistRepository.existsByBuyerIdAndListingId(buyerId, listingId);
        long count = waitlistRepository.findByListingIdOrderByJoinedAtAsc(listingId).size();
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("onWaitlist", onWaitlist);
        result.put("total", count);
        if (onWaitlist) {
            long pos = waitlistRepository.findByListingIdOrderByJoinedAtAsc(listingId).stream()
                    .takeWhile(e -> !e.getBuyer().getId().equals(buyerId)).count() + 1;
            result.put("position", pos);
        }
        return result;
    }
}
