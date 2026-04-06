package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.ListingRequest;
import com.masterchefcuts.dto.ListingResponse;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.services.ListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final ParticipantRepo participantRepo;

    @GetMapping
    public ResponseEntity<List<ListingResponse>> getAll(
            @RequestParam(required = false) String zip,
            @RequestParam(required = false) String animal,
            @RequestParam(required = false) String farmerId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(listingService.getAll(zip, animal, farmerId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListingResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(listingService.getById(id));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<List<ListingResponse>> getMyListings(@AuthenticationPrincipal String farmerId) {
        return ResponseEntity.ok(listingService.getByFarmer(farmerId));
    }

    @PostMapping
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<ListingResponse> create(@AuthenticationPrincipal String farmerId,
                                                   @Valid @RequestBody ListingRequest req) {
        Participant farmer = participantRepo.findById(farmerId)
                .orElseThrow(() -> new RuntimeException("Farmer not found"));
        if (!farmer.isApproved())
            throw new RuntimeException("Your account is pending admin approval before you can post listings.");
        return ResponseEntity.ok(listingService.create(farmerId, req));
    }

    @PatchMapping("/{id}/processing-date")
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<ListingResponse> setProcessingDate(
            @AuthenticationPrincipal String farmerId,
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(listingService.setProcessingDate(id, farmerId, date));
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<ListingResponse> uploadPhoto(
            @AuthenticationPrincipal String farmerId,
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(listingService.uploadPhoto(id, farmerId, file));
    }
}
