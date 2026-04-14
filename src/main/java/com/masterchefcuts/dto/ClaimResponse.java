package com.masterchefcuts.dto;

import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ClaimResponse {
    private Long id;
    private Long listingId;
    private AnimalType animalType;
    private String breed;
    private String sourceFarm;
    private String zipCode;
    private ListingStatus listingStatus;
    private Long cutId;
    private String cutLabel;
    private LocalDateTime claimedAt;
    private LocalDateTime expiresAt;
    private boolean paid;
}
