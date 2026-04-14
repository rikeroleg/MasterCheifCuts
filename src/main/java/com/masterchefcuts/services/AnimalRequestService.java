package com.masterchefcuts.services;

import com.masterchefcuts.dto.AnimalRequestRequest;
import com.masterchefcuts.dto.AnimalRequestResponse;
import com.masterchefcuts.dto.FulfillRequestBody;
import com.masterchefcuts.enums.AnimalRequestStatus;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.model.AnimalRequest;
import com.masterchefcuts.model.Claim;
import com.masterchefcuts.model.Cut;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.AnimalRequestRepository;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.CutRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnimalRequestService {

    private final AnimalRequestRepository animalRequestRepository;
    private final ParticipantRepo participantRepo;
    private final ListingRepository listingRepository;
    private final CutRepository cutRepository;
    private final ClaimRepository claimRepository;
    private final NotificationService notificationService;
    private final ListingService listingService;

    @Transactional
    public AnimalRequestResponse create(String buyerId, AnimalRequestRequest req) {
        Participant buyer = participantRepo.findById(buyerId)
                .orElseThrow(() -> new RuntimeException("Buyer not found"));

        AnimalRequest request = AnimalRequest.builder()
                .buyer(buyer)
                .animalType(req.getAnimalType())
                .breed(req.getBreed())
                .description(req.getDescription())
                .zipCode(req.getZipCode())
                .cutLabels(new ArrayList<>(req.getCutLabels()))
                .build();

        return toDto(animalRequestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<AnimalRequestResponse> getOpen() {
        return animalRequestRepository.findByStatusOrderByCreatedAtDesc(AnimalRequestStatus.OPEN)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AnimalRequestResponse> getMyRequests(String buyerId) {
        return animalRequestRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Farmer assumes/fulfills a buyer request.
     * Creates a Listing from the request data + farmer-supplied weight/price,
     * then auto-claims the buyer's requested cuts.
     */
    @Transactional
    public AnimalRequestResponse fulfill(Long requestId, String farmerId, FulfillRequestBody body) {
        AnimalRequest request = animalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (request.getStatus() != AnimalRequestStatus.OPEN)
            throw new RuntimeException("This request is no longer open");

        Participant farmer = participantRepo.findById(farmerId)
                .orElseThrow(() -> new RuntimeException("Farmer not found"));

        if (!farmer.isApproved())
            throw new RuntimeException("Your account must be approved before fulfilling requests");

        Participant buyer = request.getBuyer();

        // Build cuts — buyer's requested cuts go first and are auto-claimed
        List<Cut> cuts = new ArrayList<>();
        Listing listing = Listing.builder()
                .farmer(farmer)
                .animalType(request.getAnimalType())
                .breed(request.getBreed())
                .weightLbs(body.getWeightLbs())
                .pricePerLb(body.getPricePerLb())
                .sourceFarm(body.getSourceFarm())
                .description(request.getDescription())
                .zipCode(request.getZipCode())
                .status(ListingStatus.ACTIVE)
                .build();

        // Create a cut entry for each requested cut label
        for (String label : request.getCutLabels()) {
            Cut cut = Cut.builder()
                    .listing(listing)
                    .label(label)
                    .claimed(true)
                    .claimedBy(buyer)
                    .claimedAt(LocalDateTime.now())
                    .build();
            cuts.add(cut);
        }

        listing.getCuts().addAll(cuts);
        Listing savedListing = listingRepository.save(listing);

        // Persist the auto-claimed cuts and create Claim records
        for (Cut cut : savedListing.getCuts()) {
            claimRepository.save(Claim.builder()
                    .buyer(buyer)
                    .listing(savedListing)
                    .cut(cut)
                    .build());
        }

        // If all cuts are claimed (buyer requested everything), mark listing fully claimed
        long total   = savedListing.getCuts().size();
        long claimed = savedListing.getCuts().stream().filter(Cut::isClaimed).count();
        if (claimed == total) {
            savedListing.setStatus(ListingStatus.FULLY_CLAIMED);
            savedListing.setFullyClaimedAt(LocalDateTime.now());
            listingRepository.save(savedListing);
        }

        // Mark request as fulfilled
        request.setStatus(AnimalRequestStatus.FULFILLED);
        request.setFulfilledByFarmer(farmer);
        request.setFulfilledListing(savedListing);
        animalRequestRepository.save(request);

        // Notify buyer
        notificationService.send(
                buyer,
                NotificationType.REQUEST_FULFILLED,
                "🎉",
                "Your animal request was fulfilled!",
                farmer.getShopName() != null ? farmer.getShopName() : farmer.getFirstName() + " " + farmer.getLastName()
                        + " has taken on your " + request.getBreed() + " request. Your cuts are reserved!",
                savedListing.getId()
        );

        return toDto(request);
    }

    @Transactional
    public void cancel(Long requestId, String buyerId) {
        AnimalRequest request = animalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!request.getBuyer().getId().equals(buyerId))
            throw new RuntimeException("Not authorized");

        if (request.getStatus() != AnimalRequestStatus.OPEN)
            throw new RuntimeException("Only open requests can be cancelled");

        request.setStatus(AnimalRequestStatus.CANCELLED);
        animalRequestRepository.save(request);
    }

    public AnimalRequestResponse toDto(AnimalRequest r) {
        return AnimalRequestResponse.builder()
                .id(r.getId())
                .animalType(r.getAnimalType())
                .breed(r.getBreed())
                .description(r.getDescription())
                .zipCode(r.getZipCode())
                .cutLabels(r.getCutLabels())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .buyerId(r.getBuyer().getId())
                .buyerName(r.getBuyer().getFirstName() + " " + r.getBuyer().getLastName())
                .buyerZip(r.getBuyer().getZipCode())
                .fulfilledByFarmerId(r.getFulfilledByFarmer() != null ? r.getFulfilledByFarmer().getId() : null)
                .fulfilledByFarmerName(r.getFulfilledByFarmer() != null
                        ? (r.getFulfilledByFarmer().getShopName() != null
                                ? r.getFulfilledByFarmer().getShopName()
                                : r.getFulfilledByFarmer().getFirstName() + " " + r.getFulfilledByFarmer().getLastName())
                        : null)
                .fulfilledListingId(r.getFulfilledListing() != null ? r.getFulfilledListing().getId() : null)
                .build();
    }
}
