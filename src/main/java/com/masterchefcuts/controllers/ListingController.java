package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.ListingRequest;
import com.masterchefcuts.dto.ListingResponse;
import com.masterchefcuts.dto.ListingUpdateRequest;
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

    @GetMapping
    public ResponseEntity<List<ListingResponse>> getAll(
            @RequestParam(required = false) String zip,
            @RequestParam(required = false) String animal,
            @RequestParam(required = false) String farmerId,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String breed,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(listingService.getAll(zip, animal, farmerId, maxPrice, page, size, q, breed));
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

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<ListingResponse> updateListing(
            @AuthenticationPrincipal String farmerId,
            @PathVariable Long id,
            @Valid @RequestBody ListingUpdateRequest req) {
        return ResponseEntity.ok(listingService.updateListing(id, farmerId, req));
    }

    @PutMapping("/{id}/close")
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<Void> closeListing(
            @AuthenticationPrincipal String farmerId,
            @PathVariable Long id) {
        listingService.closeListing(id, farmerId);
        return ResponseEntity.noContent().build();
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
