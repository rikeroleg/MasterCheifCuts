package com.masterchefcuts.services;

import com.masterchefcuts.dto.ListingRequest;
import com.masterchefcuts.dto.ListingResponse;
import com.masterchefcuts.dto.CutRequest;
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
import com.masterchefcuts.services.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private ParticipantRepo participantRepo;
    @Mock private ClaimRepository claimRepository;
    @Mock private WaitlistRepository waitlistRepository;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private AuditService auditService;
    @Mock private StorageService storageService;
    @Mock private MultipartFile file;

    @InjectMocks private ListingService listingService;

    private Participant farmer;
    private Participant buyer;
    private Listing listing;

    @BeforeEach
    void setUp() {
        farmer = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm")
                .shopName("Jane's Farm").role(com.masterchefcuts.enums.Role.FARMER)
                .email("jane@farm.com").password("pass")
                .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        buyer = Participant.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .role(com.masterchefcuts.enums.Role.BUYER)
                .email("bob@buyer.com").password("pass")
                .street("2 Main St").city("Town").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        Cut cut = Cut.builder().id(10L).label("Ribeye").claimed(false).build();
        listing = Listing.builder()
                .id(1L).farmer(farmer).animalType(AnimalType.BEEF).breed("Angus")
                .weightLbs(500).pricePerLb(10.0).sourceFarm("Jane's Farm")
                .description("Premium beef").zipCode("12345").status(ListingStatus.ACTIVE)
                .postedAt(LocalDateTime.now()).build();
        listing.getCuts().add(cut);
        cut.setListing(listing);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_success_returnsListingResponse() {
        ListingRequest req = buildListingRequest();
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(farmer));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        ListingResponse response = listingService.create("farmer-1", req);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getBreed()).isEqualTo("Angus");
    }

    @Test
    void create_throwsWhenFarmerNotFound() {
        when(participantRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.create("missing", buildListingRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Farmer not found");
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    void getAll_noFilters_returnsActiveListings() {
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing)));

        List<ListingResponse> result = listingService.getAll(null, null, null, null, 0, 20);

        assertThat(result).hasSize(1);
    }

    @Test
    void getAll_byAnimalType_filtersCorrectly() {
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing)));

        List<ListingResponse> result = listingService.getAll(null, "BEEF", null, null, 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAnimalType()).isEqualTo(AnimalType.BEEF);
    }

    @Test
    void getAll_byZipCode_filtersCorrectly() {
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing)));

        List<ListingResponse> result = listingService.getAll("12345", null, null, null, 0, 20);

        assertThat(result).hasSize(1);
    }

    @Test
    void getAll_withKeywordQ_delegatesToSpecAndReturnsMatches() {
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing)));

        List<ListingResponse> result = listingService.getAll(null, null, null, null, 0, 20, "angus");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBreed()).isEqualTo("Angus");
    }

    @Test
    void getAll_withBlankQ_doesNotAddKeywordSpec() {
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing)));

        // blank q should be treated same as null — still returns results
        List<ListingResponse> result = listingService.getAll(null, null, null, null, 0, 20, "   ");

        assertThat(result).hasSize(1);
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_success_returnsListing() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        ListingResponse result = listingService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(listingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.getById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Listing not found");
    }

    // ── getByFarmer ───────────────────────────────────────────────────────────

    @Test
    void getByFarmer_returnsListingsForFarmer() {
        when(listingRepository.findByFarmerIdOrderByPostedAtDesc("farmer-1"))
                .thenReturn(List.of(listing));

        List<ListingResponse> result = listingService.getByFarmer("farmer-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFarmerId()).isEqualTo("farmer-1");
    }

    // ── setProcessingDate ─────────────────────────────────────────────────────

    @Test
    void setProcessingDate_success_notifiesBuyers() {
        LocalDate date = LocalDate.now().plusDays(7);
        Claim claim = Claim.builder().buyer(buyer).listing(listing).build();

        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(claimRepository.findByListingIdOrderByClaimedAtAsc(1L)).thenReturn(List.of(claim));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        listingService.setProcessingDate(1L, "farmer-1", date);

        assertThat(listing.getProcessingDate()).isEqualTo(date);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.PROCESSING);
        verify(notificationService).send(eq(buyer), eq(NotificationType.PROCESSING_SET),
                anyString(), anyString(), anyString(), eq(1L));
        verify(emailService).sendProcessingDateSet(anyList(), eq(listing), eq(date));
    }

    @Test
    void setProcessingDate_throwsWhenNotAuthorized() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.setProcessingDate(1L, "other-farmer", LocalDate.now()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void setProcessingDate_throwsWhenListingNotFound() {
        when(listingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.setProcessingDate(99L, "farmer-1", LocalDate.now()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Listing not found");
    }

    // ── toDto ─────────────────────────────────────────────────────────────────

    @Test
    void toDto_mapsAllFieldsCorrectly() {
        ListingResponse dto = listingService.toDto(listing);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getAnimalType()).isEqualTo(AnimalType.BEEF);
        assertThat(dto.getBreed()).isEqualTo("Angus");
        assertThat(dto.getFarmerId()).isEqualTo("farmer-1");
        assertThat(dto.getTotalCuts()).isEqualTo(1);
        assertThat(dto.getClaimedCuts()).isEqualTo(0);
    }

    @Test
    void toDto_countsClaimedCutsCorrectly() {
        listing.getCuts().get(0).setClaimed(true);
        listing.getCuts().get(0).setClaimedBy(buyer);

        ListingResponse dto = listingService.toDto(listing);

        assertThat(dto.getClaimedCuts()).isEqualTo(1);
        assertThat(dto.getCuts().get(0).getClaimedByName()).isEqualTo("Bob Buyer");
    }

    // ── uploadPhoto ───────────────────────────────────────────────────────────

    @Test
    void uploadPhoto_s3NotAvailable_throwsRuntime() {
        // storageService is NOT injected into listingService (optional @Autowired) — remains null
        assertThatThrownBy(() -> listingService.uploadPhoto(1L, "farmer-1", file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void uploadPhoto_invalidMimeType_throwsIllegalArgument() {
        ReflectionTestUtils.setField(listingService, "storageService", storageService);
        when(file.getContentType()).thenReturn("application/pdf");

        assertThatThrownBy(() -> listingService.uploadPhoto(1L, "farmer-1", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPEG, PNG");
    }

    @Test
    void uploadPhoto_fileTooLarge_throwsIllegalArgument() {
        ReflectionTestUtils.setField(listingService, "storageService", storageService);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(6L * 1024 * 1024); // 6 MB > 5 MB limit

        assertThatThrownBy(() -> listingService.uploadPhoto(1L, "farmer-1", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void uploadPhoto_listingNotFound_throwsRuntime() {
        ReflectionTestUtils.setField(listingService, "storageService", storageService);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(1024L);
        when(listingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.uploadPhoto(99L, "farmer-1", file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Listing not found");
    }

    @Test
    void uploadPhoto_notAuthorizedFarmer_throwsRuntime() {
        ReflectionTestUtils.setField(listingService, "storageService", storageService);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(1024L);
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.uploadPhoto(1L, "other-farmer", file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void uploadPhoto_success_jpeg_returnsUpdatedListing() throws Exception {
        ReflectionTestUtils.setField(listingService, "storageService", storageService);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(1024L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[10]));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(storageService.upload(anyString(), any(), anyLong(), anyString())).thenReturn("https://cdn.example.com/cover.jpg");
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        ListingResponse response = listingService.uploadPhoto(1L, "farmer-1", file);

        assertThat(response).isNotNull();
        assertThat(listing.getImageUrl()).isEqualTo("https://cdn.example.com/cover.jpg");
    }

    @Test
    void uploadPhoto_success_png_usesPngExtension() throws Exception {
        ReflectionTestUtils.setField(listingService, "storageService", storageService);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(1024L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[10]));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(storageService.upload(anyString(), any(), anyLong(), anyString())).thenReturn("https://cdn.example.com/cover.png");
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        ListingResponse response = listingService.uploadPhoto(1L, "farmer-1", file);

        assertThat(response).isNotNull();
        verify(storageService).upload(argThat(key -> key.endsWith(".png")), any(), anyLong(), eq("image/png"));
    }

    @Test
    void uploadPhoto_success_webp_usesWebpExtension() throws Exception {
        ReflectionTestUtils.setField(listingService, "storageService", storageService);
        when(file.getContentType()).thenReturn("image/webp");
        when(file.getSize()).thenReturn(1024L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[10]));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(storageService.upload(anyString(), any(), anyLong(), anyString())).thenReturn("https://cdn.example.com/cover.webp");
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        ListingResponse response = listingService.uploadPhoto(1L, "farmer-1", file);

        assertThat(response).isNotNull();
        verify(storageService).upload(argThat(key -> key.endsWith(".webp")), any(), anyLong(), eq("image/webp"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ListingRequest buildListingRequest() {
        ListingRequest req = new ListingRequest();
        req.setAnimalType(AnimalType.BEEF);
        req.setBreed("Angus");
        req.setWeightLbs(500);
        req.setPricePerLb(10.0);
        req.setZipCode("12345");
        CutRequest cr1 = new CutRequest(); cr1.setLabel("Ribeye");
        CutRequest cr2 = new CutRequest(); cr2.setLabel("Brisket");
        req.setCuts(List.of(cr1, cr2));
        return req;
    }

    // ── updateListing ─────────────────────────────────────────────────────────

    @Test
    void updateListing_success_changesBreedOnActiveListing() {
        ListingUpdateRequest req = new ListingUpdateRequest();
        req.setBreed("Hereford");
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);
        when(listingRepository.findByIdWithCuts(1L)).thenReturn(Optional.of(listing));

        ListingResponse response = listingService.updateListing(1L, "farmer-1", req);

        assertThat(response).isNotNull();
        assertThat(listing.getBreed()).isEqualTo("Hereford");
    }

    @Test
    void updateListing_throwsWhenListingNotFound() {
        when(listingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.updateListing(99L, "farmer-1", new ListingUpdateRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Listing not found");
    }

    @Test
    void updateListing_throwsWhenNotAuthorized() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.updateListing(1L, "other-farmer", new ListingUpdateRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void updateListing_throwsWhenListingIsComplete() {
        listing.setStatus(ListingStatus.COMPLETE);
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.updateListing(1L, "farmer-1", new ListingUpdateRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed or closed");
    }

    @Test
    void updateListing_throwsWhenListingIsClosed() {
        listing.setStatus(ListingStatus.CLOSED);
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.updateListing(1L, "farmer-1", new ListingUpdateRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed or closed");
    }

    @Test
    void updateListing_throwsBreedChangeOnNonActiveListing() {
        listing.setStatus(ListingStatus.PROCESSING);
        ListingUpdateRequest req = new ListingUpdateRequest();
        req.setBreed("Hereford");
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.updateListing(1L, "farmer-1", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void updateListing_processingDateChanged_notifiesBuyers() {
        java.time.LocalDate newDate = java.time.LocalDate.now().plusDays(10);
        listing.setProcessingDate(java.time.LocalDate.now().plusDays(5));
        ListingUpdateRequest req = new ListingUpdateRequest();
        req.setProcessingDate(newDate);
        Claim claim = Claim.builder().buyer(buyer).listing(listing).build();

        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);
        when(claimRepository.findByListingIdOrderByClaimedAtAsc(1L)).thenReturn(List.of(claim));
        when(listingRepository.findByIdWithCuts(1L)).thenReturn(Optional.of(listing));

        listingService.updateListing(1L, "farmer-1", req);

        verify(notificationService).send(eq(buyer), eq(NotificationType.PROCESSING_SET),
                anyString(), anyString(), anyString(), eq(1L));
    }

    // ── closeListing ──────────────────────────────────────────────────────────

    @Test
    void closeListing_success_releasesUnpaidClaimsAndNotifiesBuyers() {
        Cut cut = listing.getCuts().get(0);
        cut.setClaimed(true);
        cut.setClaimedBy(buyer);
        Claim claim = Claim.builder().buyer(buyer).listing(listing).cut(cut).paid(false).build();

        when(listingRepository.findByIdWithCuts(1L)).thenReturn(Optional.of(listing));
        when(claimRepository.findByListingIdOrderByClaimedAtAsc(1L)).thenReturn(List.of(claim));
        when(waitlistRepository.findByListingIdOrderByJoinedAtAsc(1L)).thenReturn(List.of());

        listingService.closeListing(1L, "farmer-1");

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.CLOSED);
        assertThat(cut.isClaimed()).isFalse();
        verify(claimRepository).deleteAll(anyList());
        verify(notificationService).send(eq(buyer), eq(NotificationType.LISTING_CLOSED),
                anyString(), anyString(), anyString(), eq(1L));
    }

    @Test
    void closeListing_throwsWhenComplete() {
        listing.setStatus(ListingStatus.COMPLETE);
        when(listingRepository.findByIdWithCuts(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.closeListing(1L, "farmer-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot close a completed listing");
    }

    @Test
    void closeListing_throwsWhenAlreadyClosed() {
        listing.setStatus(ListingStatus.CLOSED);
        when(listingRepository.findByIdWithCuts(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.closeListing(1L, "farmer-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void closeListing_notifiesWaitlistMembers() {
        when(listingRepository.findByIdWithCuts(1L)).thenReturn(Optional.of(listing));
        when(claimRepository.findByListingIdOrderByClaimedAtAsc(1L)).thenReturn(List.of());
        WaitlistEntry entry = new WaitlistEntry();
        entry.setBuyer(buyer);
        when(waitlistRepository.findByListingIdOrderByJoinedAtAsc(1L)).thenReturn(List.of(entry));

        listingService.closeListing(1L, "farmer-1");

        verify(notificationService).send(eq(buyer), eq(NotificationType.LISTING_CLOSED),
                anyString(), anyString(), anyString(), eq(1L));
    }

    @Test
    void closeListing_throwsWhenNotAuthorized() {
        when(listingRepository.findByIdWithCuts(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.closeListing(1L, "other-farmer"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Not authorized");
    }

    // ── getAll — additional filter coverage ───────────────────────────────────

    @Test
    void getAll_withFarmerId_returnsByFarmerDirectly() {
        when(listingRepository.findByFarmerIdOrderByPostedAtDesc(eq("farmer-1"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(listing)));

        List<ListingResponse> result = listingService.getAll(null, null, "farmer-1", null, 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFarmerId()).isEqualTo("farmer-1");
    }

    @Test
    void getAll_withMaxPricePerLb_filtersCorrectly() {
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing)));

        List<ListingResponse> result = listingService.getAll(null, null, null, 15.0, 0, 20);

        assertThat(result).hasSize(1);
    }

    @Test
    void getAll_withBreed_filtersCorrectly() {
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing)));

        List<ListingResponse> result = listingService.getAll(null, null, null, null, 0, 20, null, "angus");

        assertThat(result).hasSize(1);
    }
}
