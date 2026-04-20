package com.masterchefcuts.services;

import com.masterchefcuts.dto.ListingResponse;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.model.Claim;
import com.masterchefcuts.model.Cut;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.CutRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final CutRepository cutRepository;
    private final ListingRepository listingRepository;
    private final ParticipantRepo participantRepo;
    private final NotificationService notificationService;
    private final ListingService listingService;
    private final EmailService emailService;

    @Transactional
    public ListingResponse claimCut(Long listingId, Long cutId, String buyerId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new RuntimeException("Listing not found"));

        if (listing.getStatus() != ListingStatus.ACTIVE)
            throw new RuntimeException("Listing is no longer accepting claims");

        Cut cut = cutRepository.findById(cutId)
                .orElseThrow(() -> new RuntimeException("Cut not found"));

        if (!cut.getListing().getId().equals(listingId))
            throw new RuntimeException("Cut does not belong to this listing");

        if (cut.isClaimed())
            throw new RuntimeException("Cut is already claimed");

        Participant buyer = participantRepo.findById(buyerId)
                .orElseThrow(() -> new RuntimeException("Buyer not found"));

        cut.setClaimed(true);
        cut.setClaimedBy(buyer);
        cut.setClaimedAt(LocalDateTime.now());
        cutRepository.save(cut);

        claimRepository.save(Claim.builder()
                .buyer(buyer)
                .listing(listing)
                .cut(cut)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build());

        String buyerName = buyer.getFirstName() + " " + buyer.getLastName();

        notificationService.send(
                listing.getFarmer(),
                NotificationType.CUT_CLAIMED,
                "🛒",
                "New cut claimed",
                buyerName + " claimed the " + cut.getLabel() + " cut on your " + listing.getBreed() + " listing.",
                listing.getId()
        );

        String farmerDisplay = listing.getFarmer().getShopName() != null && !listing.getFarmer().getShopName().isBlank()
                ? listing.getFarmer().getShopName()
                : listing.getFarmer().getFirstName() + " " + listing.getFarmer().getLastName();

        notificationService.send(
                buyer,
                NotificationType.CUT_CLAIMED,
                "✅",
                "Cut claimed successfully",
                "You claimed the " + cut.getLabel() + " cut from " + farmerDisplay + ".",
                listing.getId()
        );

        emailService.sendClaimConfirmation(buyer, listing, cut.getLabel());

        long totalCuts   = cutRepository.countByListingId(listingId);
        long claimedCuts = cutRepository.countByListingIdAndClaimedTrue(listingId);

        if (claimedCuts == totalCuts) {
            listing.setStatus(ListingStatus.FULLY_CLAIMED);
            listing.setFullyClaimedAt(LocalDateTime.now());
            listingRepository.save(listing);

            notificationService.send(
                    listing.getFarmer(),
                    NotificationType.LISTING_FULL,
                    "🎉",
                    "Listing fully claimed!",
                    "Your " + listing.getBreed() + " listing is 100% claimed. Set a processing date to notify all buyers.",
                    listing.getId()
            );

            List<Participant> buyers = claimRepository.findByListingIdOrderByClaimedAtAsc(listingId)
                    .stream()
                    .map(Claim::getBuyer)
                    .distinct()
                    .collect(Collectors.toList());

            for (Participant b : buyers) {
                notificationService.send(
                        b,
                        NotificationType.LISTING_FULL,
                        "🎉",
                        "Your animal is fully claimed!",
                        "The " + listing.getBreed() + " from " + farmerDisplay
                                + " is fully claimed. The farmer will set a processing date soon.",
                        listing.getId()
                );
            }

            emailService.sendPoolFullToFarmer(listing.getFarmer(), listing);
            emailService.sendPoolFullToBuyers(buyers, listing);
        }

        return listingService.toDto(listingRepository.findById(listingId).get());
    }

    @Transactional
    public void unclaimCut(Long claimId, String buyerId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found"));

        if (!claim.getBuyer().getId().equals(buyerId))
            throw new AccessDeniedException("Not authorized to unclaim this cut");

        if (claim.isPaid())
            throw new RuntimeException("Cannot unclaim a cut that has already been paid for");

        Cut cut = claim.getCut();
        cut.setClaimed(false);
        cut.setClaimedBy(null);
        cut.setClaimedAt(null);
        cutRepository.save(cut);

        Listing listing = claim.getListing();
        claimRepository.delete(claim);

        // Reactivate listing if it was fully claimed
        if (listing.getStatus() == ListingStatus.FULLY_CLAIMED) {
            listing.setStatus(ListingStatus.ACTIVE);
            listing.setFullyClaimedAt(null);
            listingRepository.save(listing);
        }
    }

    public List<com.masterchefcuts.model.Claim> getClaimsForBuyer(String buyerId) {
        return claimRepository.findByBuyerIdOrderByClaimedAtDesc(buyerId);
    }

    public List<com.masterchefcuts.dto.ClaimResponse> getClaimResponsesForBuyer(String buyerId) {
                return claimRepository.findClaimSummariesByBuyerId(buyerId).stream()
                .map(c -> com.masterchefcuts.dto.ClaimResponse.builder()
                        .id(c.getId())
                        .listingId(c.getListing().getId())
                        .animalType(c.getListing().getAnimalType())
                        .breed(c.getListing().getBreed())
                        .sourceFarm(c.getListing().getSourceFarm())
                        .zipCode(c.getListing().getZipCode())
                        .listingStatus(c.getListing().getStatus())
                        .cutId(c.getCut().getId())
                        .cutLabel(c.getCut().getLabel())
                        .claimedAt(c.getClaimedAt())
                        .expiresAt(c.getExpiresAt())
                        .paid(c.isPaid())
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    public List<com.masterchefcuts.model.Claim> getClaimsForListing(Long listingId) {
        return claimRepository.findByListingIdOrderByClaimedAtAsc(listingId);
    }
}
