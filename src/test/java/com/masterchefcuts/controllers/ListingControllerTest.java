package com.masterchefcuts.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.ListingRequest;
import com.masterchefcuts.dto.ListingResponse;
import com.masterchefcuts.dto.ListingUpdateRequest;
import com.masterchefcuts.dto.CutRequest;
import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.exception.AppException;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.services.ListingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ListingController.class)
@AutoConfigureMockMvc(addFilters = false)
class ListingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ListingService listingService;
    @MockBean ParticipantRepo participantRepo;
    @MockBean JwtUtil jwtUtil;

    private ListingResponse sampleListing;
    private Participant approvedFarmer;
    private Participant unapprovedFarmer;

    @BeforeEach
    void setUp() {
        sampleListing = ListingResponse.builder()
                .id(1L).animalType(AnimalType.BEEF).breed("Angus")
                .weightLbs(500).pricePerLb(10.0).zipCode("12345")
                .status(ListingStatus.ACTIVE).farmerId("farmer-1")
                .farmerName("Jane Farm").totalCuts(2).claimedCuts(0)
                .postedAt(LocalDateTime.now()).cuts(List.of()).build();

        approvedFarmer = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm")
                .role(Role.FARMER).email("jane@farm.com").password("pass")
                .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        unapprovedFarmer = Participant.builder()
                .id("farmer-2").firstName("New").lastName("Farmer")
                .role(Role.FARMER).email("new@farm.com").password("pass")
                .street("2 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(false).build();
    }

    private UsernamePasswordAuthenticationToken farmerAuth(String farmerId) {
        return new UsernamePasswordAuthenticationToken(farmerId, null,
                List.of(new SimpleGrantedAuthority("ROLE_FARMER")));
    }

    // ── GET /api/listings ─────────────────────────────────────────────────────

    @Test
    void getAll_noFilters_returns200WithList() throws Exception {
        when(listingService.getAll(null, null, null, null, 0, 20, null, null)).thenReturn(List.of(sampleListing));

        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getAll_withZipAndAnimal_passesBothParams() throws Exception {
        when(listingService.getAll("12345", "BEEF", null, null, 0, 20, null, null)).thenReturn(List.of(sampleListing));

        mockMvc.perform(get("/api/listings").param("zip", "12345").param("animal", "BEEF"))
                .andExpect(status().isOk());
    }

    @Test
    void getAll_withKeywordQ_passesQToService() throws Exception {
        when(listingService.getAll(null, null, null, null, 0, 20, "angus", null)).thenReturn(List.of(sampleListing));

        mockMvc.perform(get("/api/listings").param("q", "angus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].breed").value("Angus"));
    }

    // ── GET /api/listings/{id} ────────────────────────────────────────────────

    @Test
    void getById_returns200WithListing() throws Exception {
        when(listingService.getById(1L)).thenReturn(sampleListing);

        mockMvc.perform(get("/api/listings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breed").value("Angus"));
    }

    @Test
    void getById_notFound_returns400() throws Exception {
        when(listingService.getById(99L)).thenThrow(new RuntimeException("Listing not found"));

        mockMvc.perform(get("/api/listings/99"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/listings/my ──────────────────────────────────────────────────

    @Test
    void getMyListings_returns200() throws Exception {
        when(listingService.getByFarmer(any())).thenReturn(List.of(sampleListing));

        mockMvc.perform(get("/api/listings/my")
                        .with(authentication(farmerAuth("farmer-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].farmerId").value("farmer-1"));
    }

    // ── POST /api/listings ────────────────────────────────────────────────────

    @Test
    void create_approvedFarmer_returns200() throws Exception {
        when(participantRepo.findById(any())).thenReturn(Optional.of(approvedFarmer));
        when(listingService.create(any(), any(ListingRequest.class))).thenReturn(sampleListing);

        mockMvc.perform(post("/api/listings")
                        .with(authentication(farmerAuth("farmer-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildListingRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_unapprovedFarmer_returns400() throws Exception {
        when(listingService.create(any(), any(ListingRequest.class)))
                .thenThrow(new AppException(HttpStatus.FORBIDDEN, "Your account is pending admin approval before you can post listings."));

        mockMvc.perform(post("/api/listings")
                        .with(authentication(farmerAuth("farmer-2")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildListingRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Your account is pending admin approval before you can post listings."));
    }

    @Test
    void create_farmerNotFound_returns400() throws Exception {
        when(listingService.create(any(), any(ListingRequest.class)))
                .thenThrow(new RuntimeException("Farmer not found"));

        mockMvc.perform(post("/api/listings")
                        .with(authentication(farmerAuth("farmer-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildListingRequest())))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/listings/{id}/processing-date ──────────────────────────────

    @Test
    void setProcessingDate_returns200() throws Exception {
        when(listingService.setProcessingDate(anyLong(), any(), any()))
                .thenReturn(sampleListing);

        mockMvc.perform(patch("/api/listings/1/processing-date")
                        .with(authentication(farmerAuth("farmer-1")))
                        .param("date", "2026-05-01"))
                .andExpect(status().isOk());
    }

    // ── POST /api/listings/{id}/photo ─────────────────────────────────────────

    @Test
    void uploadPhoto_returns200() throws Exception {
        when(listingService.uploadPhoto(anyLong(), any(), any())).thenReturn(sampleListing);

        MockMultipartFile photo = new MockMultipartFile(
                "file", "cover.jpg", "image/jpeg", "fake-image-data".getBytes());

        mockMvc.perform(multipart("/api/listings/1/photo")
                        .file(photo)
                        .with(authentication(farmerAuth("farmer-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ── PATCH /api/listings/{id} ──────────────────────────────────────────────

    @Test
    void updateListing_returns200() throws Exception {
        when(listingService.updateListing(eq(1L), any(), any(ListingUpdateRequest.class)))
                .thenReturn(sampleListing);

        ListingUpdateRequest req = new ListingUpdateRequest();
        req.setBreed("Hereford");
        req.setPricePerLb(11.0);

        mockMvc.perform(patch("/api/listings/1")
                        .with(authentication(farmerAuth("farmer-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateListing_serviceThrows_returns400() throws Exception {
        when(listingService.updateListing(eq(99L), any(), any()))
                .thenThrow(new RuntimeException("Listing not found"));

        ListingUpdateRequest req = new ListingUpdateRequest();
        req.setBreed("Hereford");

        mockMvc.perform(patch("/api/listings/99")
                        .with(authentication(farmerAuth("farmer-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/listings/{id}/close ──────────────────────────────────────────

    @Test
    void closeListing_returns204() throws Exception {
        doNothing().when(listingService).closeListing(1L, "farmer-1");

        mockMvc.perform(put("/api/listings/1/close")
                        .with(authentication(farmerAuth("farmer-1"))))
                .andExpect(status().isNoContent());
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
}
