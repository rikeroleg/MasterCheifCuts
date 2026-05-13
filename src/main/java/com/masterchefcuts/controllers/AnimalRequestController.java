package com.masterchefcuts.controllers;

import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.AnimalRequestRequest;
import com.masterchefcuts.dto.AnimalRequestResponse;
import com.masterchefcuts.dto.FulfillRequestBody;
import com.masterchefcuts.services.AnimalRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/animal-requests")
@RequiredArgsConstructor
public class AnimalRequestController {

    private final AnimalRequestService animalRequestService;
    private final JwtUtil jwtUtil;

    /** Buyer submits a new animal request */
    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<AnimalRequestResponse> create(
            @Valid @RequestBody AnimalRequestRequest req,
            @RequestHeader("Authorization") String authHeader) {
        String buyerId = extractId(authHeader);
        return ResponseEntity.ok(animalRequestService.create(buyerId, req));
    }

    /** Anyone (especially farmers) can browse open requests */
    @GetMapping
    public ResponseEntity<List<AnimalRequestResponse>> getOpen() {
        return ResponseEntity.ok(animalRequestService.getOpen());
    }

    /** Buyer sees their own requests */
    @GetMapping("/my")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<List<AnimalRequestResponse>> getMine(
            @RequestHeader("Authorization") String authHeader) {
        String buyerId = extractId(authHeader);
        return ResponseEntity.ok(animalRequestService.getMyRequests(buyerId));
    }

    /** Farmer assumes/fulfills a request — creates the listing and auto-claims buyer's cuts */
    @PostMapping("/{id}/fulfill")
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<AnimalRequestResponse> fulfill(
            @PathVariable Long id,
            @Valid @RequestBody FulfillRequestBody body,
            @RequestHeader("Authorization") String authHeader) {
        String farmerId = extractId(authHeader);
        return ResponseEntity.ok(animalRequestService.fulfill(id, farmerId, body));
    }

    /** Buyer cancels their own open request */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<Void> cancel(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        String buyerId = extractId(authHeader);
        animalRequestService.cancel(id, buyerId);
        return ResponseEntity.noContent().build();
    }

    /** Buyer edits their own open request */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<AnimalRequestResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AnimalRequestRequest req,
            @RequestHeader("Authorization") String authHeader) {
        String buyerId = extractId(authHeader);
        return ResponseEntity.ok(animalRequestService.updateRequest(id, buyerId, req));
    }

    private String extractId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.extractId(token);
    }
}
