package com.masterchefcuts.services;

import com.masterchefcuts.model.Claim;
import com.masterchefcuts.model.Dispute;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.DisputeRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final ClaimRepository claimRepository;
    private final ParticipantRepo participantRepo;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public Dispute createDispute(String buyerId, Long claimId, Long listingId, String type, String description) {
        if (disputeRepository.existsByClaimIdAndStatus(claimId, "OPEN")) {
            throw new IllegalStateException("An open dispute already exists for this claim");
        }

        Participant buyer = participantRepo.findById(buyerId).orElse(null);
        String buyerName = buyer != null ? (buyer.getFirstName() + " " + buyer.getLastName()).trim() : null;

        String farmerId = null;
        String farmerName = null;
        if (claimId != null) {
            Claim claim = claimRepository.findById(claimId).orElse(null);
            if (claim != null && claim.getListing() != null && claim.getListing().getFarmer() != null) {
                Participant farmer = claim.getListing().getFarmer();
                farmerId = farmer.getId();
                farmerName = (farmer.getFirstName() + " " + farmer.getLastName()).trim();
            }
        }

        Dispute dispute = Dispute.builder()
                .buyerId(buyerId)
                .buyerName(buyerName)
                .farmerId(farmerId)
                .farmerName(farmerName)
                .claimId(claimId)
                .listingId(listingId)
                .type(type)
                .description(description)
                .build();

        Dispute saved = disputeRepository.save(dispute);

        // SSE + email to buyer
        if (buyer != null) {
            notificationService.send(buyer, NotificationType.DISPUTE_OPENED, "⚠️",
                    "Dispute filed",
                    "Your dispute on listing #" + listingId + " has been submitted. Our team will review it shortly.",
                    listingId);
            emailService.sendDisputeOpened(buyer, saved.getId(), listingId, true);
        }

        // SSE + email to farmer
        if (farmerId != null) {
            Participant farmer = participantRepo.findById(farmerId).orElse(null);
            if (farmer != null) {
                notificationService.send(farmer, NotificationType.DISPUTE_OPENED, "⚠️",
                        "Dispute filed on your listing",
                        "A buyer has opened a dispute on listing #" + listingId + ". Our team will review it.",
                        listingId);
                emailService.sendDisputeOpened(farmer, saved.getId(), listingId, false);
            }
        }

        return saved;
    }

    public List<Dispute> getAllDisputes() {
        return disputeRepository.findAllByOrderByCreatedAtDesc();
    }

    public Dispute resolveDispute(Long id, String resolution) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + id));
        dispute.setStatus("RESOLVED");
        dispute.setResolution(resolution);
        dispute.setResolvedAt(LocalDateTime.now());
        return disputeRepository.save(dispute);
    }
}
