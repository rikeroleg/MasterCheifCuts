package com.masterchefcuts.scheduler;

import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.model.Claim;
import com.masterchefcuts.model.Cut;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.CutRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimExpiryScheduler {

    private final ClaimRepository claimRepository;
    private final CutRepository cutRepository;
    private final ListingRepository listingRepository;
    private final NotificationService notificationService;

    /**
     * Runs every minute. Finds unpaid claims past their expiresAt,
     * releases the cut back to inventory, removes the claim,
     * and reopens the listing if it was FULLY_CLAIMED.
     */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expireUnpaidClaims() {
        List<Claim> expired = claimRepository.findByPaidFalseAndExpiresAtBefore(LocalDateTime.now());
        if (expired.isEmpty()) return;

        log.info("Expiring {} unpaid claim(s)", expired.size());

        Set<Long> affectedListingIds = new HashSet<>();

        for (Claim claim : expired) {
            Cut cut = claim.getCut();
            Listing listing = claim.getListing();

            // Release the cut
            cut.setClaimed(false);
            cut.setClaimedBy(null);
            cut.setClaimedAt(null);
            cutRepository.save(cut);

            // Notify buyer
            notificationService.send(
                    claim.getBuyer(),
                    NotificationType.CUT_CLAIMED,
                    "⏰",
                    "Claim expired",
                    "Your claim on the " + cut.getLabel() + " cut from the "
                            + listing.getBreed() + " listing has expired because payment was not received in time.",
                    listing.getId()
            );

            affectedListingIds.add(listing.getId());

            // Delete the claim
            claimRepository.delete(claim);

            log.info("Expired claim #{} — cut '{}' released on listing #{}", claim.getId(), cut.getLabel(), listing.getId());
        }

        // Reopen any FULLY_CLAIMED listings that now have available cuts
        for (Long listingId : affectedListingIds) {
            listingRepository.findById(listingId).ifPresent(listing -> {
                if (listing.getStatus() == ListingStatus.FULLY_CLAIMED) {
                    listing.setStatus(ListingStatus.ACTIVE);
                    listing.setFullyClaimedAt(null);
                    listingRepository.save(listing);
                    log.info("Listing #{} reopened (was FULLY_CLAIMED, now has available cuts)", listingId);
                }
            });
        }
    }
}
