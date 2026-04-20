package com.masterchefcuts.services;

import com.masterchefcuts.dto.ListingRequest;
import com.masterchefcuts.dto.ListingResponse;
import com.masterchefcuts.dto.ListingUpdateRequest;
import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.model.Claim;
import com.masterchefcuts.model.Cut;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.model.WaitlistEntry;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.repositories.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;
    private final ParticipantRepo participantRepo;
    private final ClaimRepository claimRepository;
    private final WaitlistRepository waitlistRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final AuditService auditService;

    // Optional — present only when a storage profile (gcp/local) is enabled.
    @Autowired(required = false)
    private StorageService storageService;

    /**
     * When true (default), farmers must complete Stripe Connect onboarding before posting listings.
     * Set stripe.connect.required=false in dev to skip this check.
     */
    @Value("${stripe.connect.required:true}")
    private boolean stripeConnectRequired;

    @Transactional
    public ListingResponse create(String farmerId, ListingRequest req) {
        Participant farmer = participantRepo.findById(farmerId)
                .orElseThrow(() -> new RuntimeException("Farmer not found"));

        if (stripeConnectRequired && !Boolean.TRUE.equals(farmer.getStripeOnboardingComplete())) {
            throw new IllegalStateException(
                    "You must connect your bank account via Stripe before posting a listing. " +
                    "Go to your profile to complete onboarding.");
        }

        Listing listing = Listing.builder()
                .farmer(farmer)
                .animalType(req.getAnimalType())
                .breed(req.getBreed())
                .weightLbs(req.getWeightLbs())
                .pricePerLb(req.getPricePerLb())
                .sourceFarm(req.getSourceFarm())
                .description(req.getDescription())
                .zipCode(req.getZipCode())
                .build();

        List<Cut> cuts = req.getCuts().stream()
                .map(cr -> Cut.builder()
                        .listing(listing)
                        .label(cr.getLabel())
                        .weightLbs(cr.getWeightLbs())
                        .build())
                .collect(Collectors.toList());

        listing.getCuts().addAll(cuts);
        Listing saved = listingRepository.save(listing);

        // Notify participants in the same ZIP (excluding the farmer) — capped at 50 to prevent flooding
        try {
            List<Participant> nearby = participantRepo.findByZipCodeAndEmailVerifiedTrue(req.getZipCode())
                    .stream()
                    .filter(p -> !p.getId().equals(farmerId))
                    .limit(50)
                    .collect(Collectors.toList());
            for (Participant p : nearby) {
                notificationService.send(p, NotificationType.NEW_LISTING_NEARBY, "🐄",
                        "New " + saved.getBreed() + " " + saved.getAnimalType() + " listing near you",
                        (saved.getFarmer().getShopName() != null ? saved.getFarmer().getShopName() : farmer.getFirstName())
                                + " just posted a new " + saved.getBreed() + " " + saved.getAnimalType()
                                + " listing in " + saved.getZipCode() + ".",
                        saved.getId());
                emailService.sendNewListingNearby(p, saved);
            }
        } catch (Exception e) {
            log.warn("Failed to send nearby listing notifications: {}", e.getMessage());
        }

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> getAll(String zipCode, String animalType, String farmerId,
                                        Double maxPricePerLb, int page, int size) {
        return getAll(zipCode, animalType, farmerId, maxPricePerLb, page, size, null, null);
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> getAll(String zipCode, String animalType, String farmerId,
                                        Double maxPricePerLb, int page, int size, String q) {
        return getAll(zipCode, animalType, farmerId, maxPricePerLb, page, size, q, null);
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> getAll(String zipCode, String animalType, String farmerId,
                                        Double maxPricePerLb, int page, int size, String q, String breed) {
        if (farmerId != null && !farmerId.isBlank()) {
            return listingRepository.findByFarmerIdOrderByPostedAtDesc(farmerId, PageRequest.of(page, size))
                    .getContent().stream().map(this::toDto).collect(Collectors.toList());
        }

        Specification<Listing> spec = Specification.where(
                (root, query, cb) -> cb.equal(root.get("status"), ListingStatus.ACTIVE));

        if (animalType != null && !animalType.isBlank()) {
            AnimalType at = AnimalType.fromString(animalType);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("animalType"), at));
        }
        if (maxPricePerLb != null) {
            spec = spec.and((root, query, cb) -> cb.le(root.get("pricePerLb"), maxPricePerLb));
        }
        if (zipCode != null && !zipCode.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("zipCode"), zipCode));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("breed")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("sourceFarm")), pattern)
            ));
        }
        if (breed != null && !breed.isBlank()) {
            String pattern = "%" + breed.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("breed")), pattern));
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "postedAt"));
        return listingRepository.findAll(spec, pageable)
                .getContent().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ListingResponse getById(Long id) {
        return toDto(listingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Listing not found")));
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> getByFarmer(String farmerId) {
        return listingRepository.findByFarmerIdOrderByPostedAtDesc(farmerId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ListingResponse setProcessingDate(Long listingId, String farmerId, java.time.LocalDate date) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new RuntimeException("Listing not found"));

        if (!listing.getFarmer().getId().equals(farmerId))
            throw new RuntimeException("Not authorized");

        listing.setProcessingDate(date);
        listing.setStatus(ListingStatus.PROCESSING);
        listingRepository.save(listing);

        List<Participant> buyers = claimRepository.findByListingIdOrderByClaimedAtAsc(listingId)
                .stream().map(Claim::getBuyer).distinct().collect(Collectors.toList());

        for (Participant buyer : buyers) {
            notificationService.send(
                    buyer,
                    NotificationType.PROCESSING_SET,
                    "\uD83D\uDCC5",
                    "Processing date set!",
                    "Your " + listing.getBreed() + " cut will be processed on " + date + ".",
                    listing.getId()
            );
        }

        emailService.sendProcessingDateSet(buyers, listing, date);

        return toDto(listingRepository.findById(listingId).get());
    }

    /**
     * Update editable fields of a listing.
     * Breed and price are restricted to ACTIVE listings;
     * description and processingDate can be updated at any non-terminal status.
     */
    @Transactional
    public ListingResponse updateListing(Long listingId, String farmerId, ListingUpdateRequest req) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new RuntimeException("Listing not found"));

        if (!listing.getFarmer().getId().equals(farmerId))
            throw new RuntimeException("Not authorized");

        if (listing.getStatus() == ListingStatus.COMPLETE
                || listing.getStatus() == ListingStatus.CLOSED)
            throw new IllegalStateException("Cannot edit a completed or closed listing");

        boolean activeOnly = listing.getStatus() != ListingStatus.ACTIVE;
        if (activeOnly && (req.getBreed() != null || req.getPricePerLb() != null))
            throw new IllegalStateException(
                    "Breed and price can only be edited while the listing is ACTIVE (" +
                    listing.getStatus() + ")" );

        boolean dateChanged = false;
        if (req.getBreed() != null && !req.getBreed().isBlank())
            listing.setBreed(req.getBreed());
        if (req.getDescription() != null)
            listing.setDescription(req.getDescription());
        if (req.getPricePerLb() != null && req.getPricePerLb() > 0)
            listing.setPricePerLb(req.getPricePerLb());
        if (req.getProcessingDate() != null) {
            dateChanged = !req.getProcessingDate().equals(listing.getProcessingDate());
            listing.setProcessingDate(req.getProcessingDate());
        }

        listingRepository.save(listing);

        // Notify claimed buyers if processing date was changed
        if (dateChanged) {
            List<Participant> buyers = claimRepository.findByListingIdOrderByClaimedAtAsc(listingId)
                    .stream().map(Claim::getBuyer).distinct().collect(Collectors.toList());
            for (Participant buyer : buyers) {
                notificationService.send(
                        buyer,
                        NotificationType.PROCESSING_SET,
                        "\uD83D\uDCC5",
                        "Processing date updated",
                        "The processing date for your " + listing.getBreed() +
                                " cut has been updated to " + req.getProcessingDate() + ".",
                        listing.getId()
                );
            }
        }

        return toDto(listingRepository.findByIdWithCuts(listingId).orElseThrow());
    }

    /**
     * Farmer closes a listing early:
     * - Unclaims all unpaid cuts and removes those claims
     * - Notifies affected buyers and waitlist members
     * - Sets listing status to CLOSED
     */
    @Transactional
    public void closeListing(Long listingId, String farmerId) {
        Listing listing = listingRepository.findByIdWithCuts(listingId)
                .orElseThrow(() -> new RuntimeException("Listing not found"));

        if (!listing.getFarmer().getId().equals(farmerId))
            throw new RuntimeException("Not authorized");

        if (listing.getStatus() == ListingStatus.COMPLETE)
            throw new IllegalStateException("Cannot close a completed listing");
        if (listing.getStatus() == ListingStatus.CLOSED)
            throw new IllegalStateException("Listing is already closed");

        // Release all unpaid claims
        List<Claim> unpaid = claimRepository.findByListingIdOrderByClaimedAtAsc(listingId)
                .stream().filter(c -> !c.isPaid()).collect(Collectors.toList());

        Set<Participant> affectedBuyers = new HashSet<>();
        for (Claim claim : unpaid) {
            Cut cut = claim.getCut();
            cut.setClaimed(false);
            cut.setClaimedBy(null);
            cut.setClaimedAt(null);
            affectedBuyers.add(claim.getBuyer());
        }
        claimRepository.deleteAll(unpaid);

        String animalDesc = listing.getBreed() + " " + listing.getAnimalType();

        // Notify buyers whose unpaid claims were removed
        for (Participant buyer : affectedBuyers) {
            notificationService.send(
                    buyer,
                    NotificationType.LISTING_CLOSED,
                    "\u2139\uFE0F",
                    "Listing closed",
                    "The listing for " + animalDesc +
                            " has been closed by the farmer. Your unpaid claim has been released.",
                    listing.getId()
            );
        }

        // Notify waitlist members
        List<WaitlistEntry> waitlisters =
                waitlistRepository.findByListingIdOrderByJoinedAtAsc(listingId);
        for (WaitlistEntry entry : waitlisters) {
            notificationService.send(
                    entry.getBuyer(),
                    NotificationType.LISTING_CLOSED,
                    "\u2139\uFE0F",
                    "Listing closed",
                    "A listing you were waiting on (" + animalDesc + ") has been closed.",
                    listing.getId()
            );
        }

        listing.setStatus(ListingStatus.CLOSED);
        listingRepository.save(listing);

        auditService.log(farmerId, "CLOSE_LISTING", String.valueOf(listingId));
    }

    @Transactional
    public ListingResponse uploadPhoto(Long listingId, String farmerId, MultipartFile file) {
        if (storageService == null)
            throw new RuntimeException("Photo upload is not available in this environment");

        // Validate MIME type — do NOT trust file extension, check content type
        String contentType = file.getContentType();
        if (contentType == null || !List.of("image/jpeg", "image/png", "image/webp").contains(contentType))
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are accepted");

        // Validate size — 5 MB max
        if (file.getSize() > 5L * 1024 * 1024)
            throw new IllegalArgumentException("Image must be under 5MB");

        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new RuntimeException("Listing not found"));

        if (!listing.getFarmer().getId().equals(farmerId))
            throw new RuntimeException("Not authorized to modify this listing");

        String ext = switch (contentType) {
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            default           -> "jpg";
        };
        String key = "listings/" + listingId + "/cover." + ext;

        try {
            String imageUrl = storageService.upload(key, file.getInputStream(), file.getSize(), contentType);
            listing.setImageUrl(imageUrl);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        return toDto(listingRepository.save(listing));
    }

    public ListingResponse toDto(Listing l) {
        List<ListingResponse.CutDto> cutDtos = l.getCuts().stream()
                .map(c -> ListingResponse.CutDto.builder()
                        .id(c.getId())
                        .label(c.getLabel())
                        .weightLbs(c.getWeightLbs())
                        .claimed(c.isClaimed())
                        .claimedByName(c.getClaimedBy() != null
                                ? c.getClaimedBy().getFirstName() + " " + c.getClaimedBy().getLastName()
                                : null)
                        .claimedAt(c.getClaimedAt())
                        .build())
                .collect(Collectors.toList());

        long claimedCount = l.getCuts().stream().filter(Cut::isClaimed).count();

        return ListingResponse.builder()
                .id(l.getId())
                .animalType(l.getAnimalType())
                .breed(l.getBreed())
                .weightLbs(l.getWeightLbs())
                .pricePerLb(l.getPricePerLb())
                .sourceFarm(l.getSourceFarm())
                .description(l.getDescription())
                .zipCode(l.getZipCode())
                .status(l.getStatus())
                .processingDate(l.getProcessingDate())
                .postedAt(l.getPostedAt())
                .fullyClaimedAt(l.getFullyClaimedAt())
                .imageUrl(l.getImageUrl())
                .farmerId(l.getFarmer().getId())
                .farmerName(l.getFarmer().getFirstName() + " " + l.getFarmer().getLastName())
                .farmerShopName(l.getFarmer().getShopName())
                .farmerBio(l.getFarmer().getBio())
                .farmerCertifications(l.getFarmer().getCertifications())
                .cuts(cutDtos)
                .totalCuts(l.getCuts().size())
                .claimedCuts((int) claimedCount)
                .build();
    }
}
