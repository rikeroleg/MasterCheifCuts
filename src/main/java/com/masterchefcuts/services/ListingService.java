package com.masterchefcuts.services;

import com.masterchefcuts.dto.ListingRequest;
import com.masterchefcuts.dto.ListingResponse;
import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.model.Claim;
import com.masterchefcuts.model.Cut;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;
    private final ParticipantRepo participantRepo;
    private final ClaimRepository claimRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    // Optional — only present under the "aws" Spring profile
    @Autowired(required = false)
    private S3Service s3Service;

    @Transactional
    public ListingResponse create(String farmerId, ListingRequest req) {
        Participant farmer = participantRepo.findById(farmerId)
                .orElseThrow(() -> new RuntimeException("Farmer not found"));

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

        List<Cut> cuts = req.getCutLabels().stream()
                .map(label -> Cut.builder().listing(listing).label(label).build())
                .collect(Collectors.toList());

        listing.getCuts().addAll(cuts);
        return toDto(listingRepository.save(listing));
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> getAll(String zipCode, String animalType) {
        List<Listing> results;
        if (animalType != null && !animalType.isBlank()) {
            results = listingRepository.findByAnimalTypeAndStatusOrderByPostedAtDesc(
                    AnimalType.fromString(animalType), ListingStatus.ACTIVE);
        } else if (zipCode != null && !zipCode.isBlank()) {
            results = listingRepository.findByZipCodeAndStatusOrderByPostedAtDesc(zipCode, ListingStatus.ACTIVE);
        } else {
            results = listingRepository.findByStatusOrderByPostedAtDesc(ListingStatus.ACTIVE);
        }
        return results.stream().map(this::toDto).collect(Collectors.toList());
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

    @Transactional
    public ListingResponse uploadPhoto(Long listingId, String farmerId, MultipartFile file) {
        if (s3Service == null)
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
            String imageUrl = s3Service.upload(key, file.getInputStream(), file.getSize(), contentType);
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
                .cuts(cutDtos)
                .totalCuts(l.getCuts().size())
                .claimedCuts((int) claimedCount)
                .build();
    }
}
