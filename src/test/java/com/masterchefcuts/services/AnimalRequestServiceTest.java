package com.masterchefcuts.services;

import com.masterchefcuts.dto.AnimalRequestRequest;
import com.masterchefcuts.dto.AnimalRequestResponse;
import com.masterchefcuts.dto.FulfillRequestBody;
import com.masterchefcuts.enums.AnimalRequestStatus;
import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.AnimalRequest;
import com.masterchefcuts.model.Cut;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.AnimalRequestRepository;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.CutRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnimalRequestServiceTest {

    @Mock private AnimalRequestRepository animalRequestRepository;
    @Mock private ParticipantRepo participantRepo;
    @Mock private ListingRepository listingRepository;
    @Mock private CutRepository cutRepository;
    @Mock private ClaimRepository claimRepository;
    @Mock private NotificationService notificationService;
    @Mock private ListingService listingService;

    @InjectMocks private AnimalRequestService animalRequestService;

    private Participant buyer;
    private Participant farmer;
    private AnimalRequest openRequest;

    @BeforeEach
    void setUp() {
        buyer = Participant.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .role(Role.BUYER).email("bob@buyer.com").password("pass")
                .street("2 Main St").city("Town").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        farmer = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm").shopName("Jane's Farm")
                .role(Role.FARMER).email("jane@farm.com").password("pass")
                .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        openRequest = AnimalRequest.builder()
                .id(1L).buyer(buyer).animalType(AnimalType.BEEF).breed("Angus")
                .description("I want premium beef").zipCode("12345")
                .cutLabels(new ArrayList<>(List.of("Ribeye", "Brisket")))
                .status(AnimalRequestStatus.OPEN).createdAt(LocalDateTime.now()).build();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_success_savesAndReturnsDto() {
        AnimalRequestRequest req = buildRequest();
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));
        when(animalRequestRepository.save(any(AnimalRequest.class))).thenReturn(openRequest);

        AnimalRequestResponse response = animalRequestService.create("buyer-1", req);

        assertThat(response).isNotNull();
        assertThat(response.getBreed()).isEqualTo("Angus");
        assertThat(response.getBuyerId()).isEqualTo("buyer-1");
        verify(animalRequestRepository).save(any(AnimalRequest.class));
    }

    @Test
    void create_throwsWhenBuyerNotFound() {
        when(participantRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> animalRequestService.create("missing", buildRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Buyer not found");
    }

    // ── getOpen ───────────────────────────────────────────────────────────────

    @Test
    void getOpen_returnsOpenRequestsAsDtos() {
        when(animalRequestRepository.findByStatusOrderByCreatedAtDesc(AnimalRequestStatus.OPEN))
                .thenReturn(List.of(openRequest));

        List<AnimalRequestResponse> result = animalRequestService.getOpen();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(AnimalRequestStatus.OPEN);
    }

    // ── getMyRequests ─────────────────────────────────────────────────────────

    @Test
    void getMyRequests_returnsRequestsForBuyer() {
        when(animalRequestRepository.findByBuyerIdOrderByCreatedAtDesc("buyer-1"))
                .thenReturn(List.of(openRequest));

        List<AnimalRequestResponse> result = animalRequestService.getMyRequests("buyer-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBuyerId()).isEqualTo("buyer-1");
    }

    // ── fulfill ───────────────────────────────────────────────────────────────

    @Test
    void fulfill_success_createsListingAndAutoClaimsCuts() {
        FulfillRequestBody body = new FulfillRequestBody();
        body.setWeightLbs(600);
        body.setPricePerLb(12.0);
        body.setSourceFarm("Jane's Farm");

        Cut savedCut1 = Cut.builder().id(1L).label("Ribeye").claimed(true).claimedBy(buyer).build();
        Cut savedCut2 = Cut.builder().id(2L).label("Brisket").claimed(true).claimedBy(buyer).build();

        Listing savedListing = Listing.builder()
                .id(10L).farmer(farmer).animalType(AnimalType.BEEF).breed("Angus")
                .weightLbs(600).pricePerLb(12.0).zipCode("12345")
                .status(ListingStatus.FULLY_CLAIMED).postedAt(LocalDateTime.now()).build();
        savedListing.getCuts().add(savedCut1);
        savedListing.getCuts().add(savedCut2);
        savedCut1.setListing(savedListing);
        savedCut2.setListing(savedListing);

        when(animalRequestRepository.findById(1L)).thenReturn(Optional.of(openRequest));
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(farmer));
        when(listingRepository.save(any(Listing.class))).thenReturn(savedListing);
        when(animalRequestRepository.save(any(AnimalRequest.class))).thenReturn(openRequest);

        AnimalRequestResponse response = animalRequestService.fulfill(1L, "farmer-1", body);

        assertThat(response).isNotNull();
        verify(listingRepository, times(2)).save(any(Listing.class));
        verify(claimRepository, atLeastOnce()).save(any());
        assertThat(openRequest.getStatus()).isEqualTo(AnimalRequestStatus.FULFILLED);
        assertThat(openRequest.getFulfilledByFarmer()).isEqualTo(farmer);
    }

    @Test
    void fulfill_throwsWhenRequestNotFound() {
        when(animalRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> animalRequestService.fulfill(99L, "farmer-1", new FulfillRequestBody()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Request not found");
    }

    @Test
    void fulfill_throwsWhenRequestNotOpen() {
        openRequest.setStatus(AnimalRequestStatus.FULFILLED);
        when(animalRequestRepository.findById(1L)).thenReturn(Optional.of(openRequest));

        assertThatThrownBy(() -> animalRequestService.fulfill(1L, "farmer-1", new FulfillRequestBody()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no longer open");
    }

    @Test
    void fulfill_throwsWhenFarmerNotFound() {
        when(animalRequestRepository.findById(1L)).thenReturn(Optional.of(openRequest));
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> animalRequestService.fulfill(1L, "farmer-1", new FulfillRequestBody()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Farmer not found");
    }

    @Test
    void fulfill_throwsWhenFarmerNotApproved() {
        farmer.setApproved(false);
        when(animalRequestRepository.findById(1L)).thenReturn(Optional.of(openRequest));
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(farmer));

        assertThatThrownBy(() -> animalRequestService.fulfill(1L, "farmer-1", new FulfillRequestBody()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("approved");
    }

    // ── cancel ────────────────────────────────────────────────────────────────

    @Test
    void cancel_success_setsStatusCancelled() {
        when(animalRequestRepository.findById(1L)).thenReturn(Optional.of(openRequest));

        animalRequestService.cancel(1L, "buyer-1");

        assertThat(openRequest.getStatus()).isEqualTo(AnimalRequestStatus.CANCELLED);
        verify(animalRequestRepository).save(openRequest);
    }

    @Test
    void cancel_throwsWhenNotAuthorized() {
        when(animalRequestRepository.findById(1L)).thenReturn(Optional.of(openRequest));

        assertThatThrownBy(() -> animalRequestService.cancel(1L, "other-buyer"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void cancel_throwsWhenRequestNotOpen() {
        openRequest.setStatus(AnimalRequestStatus.FULFILLED);
        when(animalRequestRepository.findById(1L)).thenReturn(Optional.of(openRequest));

        assertThatThrownBy(() -> animalRequestService.cancel(1L, "buyer-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Only open requests can be cancelled");
    }

    @Test
    void cancel_throwsWhenRequestNotFound() {
        when(animalRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> animalRequestService.cancel(99L, "buyer-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Request not found");
    }

    @Test
    void fulfill_partialClaim_doesNotMarkFullyClaimed() {
        // Cut labels = ["Ribeye", "Brisket"], but savedListing has an extra unclaimed "Sirloin"
        FulfillRequestBody body = new FulfillRequestBody();
        body.setWeightLbs(600);
        body.setPricePerLb(12.0);
        body.setSourceFarm("Jane's Farm");

        Cut claimedCut1 = Cut.builder().id(1L).label("Ribeye").claimed(true).claimedBy(buyer).build();
        Cut claimedCut2 = Cut.builder().id(2L).label("Brisket").claimed(true).claimedBy(buyer).build();
        Cut unclaimedCut = Cut.builder().id(3L).label("Sirloin").claimed(false).build();

        Listing partialListing = Listing.builder()
                .id(10L).farmer(farmer).animalType(AnimalType.BEEF).breed("Angus")
                .weightLbs(600).pricePerLb(12.0).zipCode("12345")
                .status(ListingStatus.ACTIVE).postedAt(LocalDateTime.now()).build();
        partialListing.getCuts().addAll(List.of(claimedCut1, claimedCut2, unclaimedCut));

        when(animalRequestRepository.findById(1L)).thenReturn(Optional.of(openRequest));
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(farmer));
        when(listingRepository.save(any(Listing.class))).thenReturn(partialListing);
        when(animalRequestRepository.save(any(AnimalRequest.class))).thenReturn(openRequest);

        AnimalRequestResponse response = animalRequestService.fulfill(1L, "farmer-1", body);

        assertThat(response).isNotNull();
        // Not all 3 cuts are claimed → listing should NOT be marked FULLY_CLAIMED
        assertThat(partialListing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        // listingRepository.save() called only once (during creation, not again to mark FULLY_CLAIMED)
        verify(listingRepository, times(1)).save(any(Listing.class));
    }

    // ── toDto ─────────────────────────────────────────────────────────────────

    @Test
    void toDto_withFulfilledFarmerNoShopName_usesFullName() {
        Participant farmerNoShop = Participant.builder()
                .id("farmer-2").firstName("Tom").lastName("Smith")
                .role(Role.FARMER).email("tom@farm.com").password("pass")
                .street("5 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();
        // shopName is null by default in builder

        openRequest.setFulfilledByFarmer(farmerNoShop);
        openRequest.setStatus(AnimalRequestStatus.FULFILLED);

        AnimalRequestResponse dto = animalRequestService.toDto(openRequest);

        assertThat(dto.getFulfilledByFarmerId()).isEqualTo("farmer-2");
        assertThat(dto.getFulfilledByFarmerName()).isEqualTo("Tom Smith");
    }

    @Test
    void toDto_withFulfilledListing_includesListingId() {
        Listing fulfilledListing = Listing.builder()
                .id(10L).farmer(farmer).animalType(AnimalType.BEEF).breed("Angus")
                .zipCode("12345").status(ListingStatus.FULLY_CLAIMED)
                .postedAt(LocalDateTime.now()).build();

        openRequest.setFulfilledListing(fulfilledListing);

        AnimalRequestResponse dto = animalRequestService.toDto(openRequest);

        assertThat(dto.getFulfilledListingId()).isEqualTo(10L);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AnimalRequestRequest buildRequest() {
        AnimalRequestRequest req = new AnimalRequestRequest();
        req.setAnimalType(AnimalType.BEEF);
        req.setBreed("Angus");
        req.setDescription("I want premium beef");
        req.setZipCode("12345");
        req.setCutLabels(List.of("Ribeye", "Brisket"));
        return req;
    }
}
