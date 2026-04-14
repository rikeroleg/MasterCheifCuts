package com.masterchefcuts.dto;

import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ListingResponse {

    private Long id;
    private AnimalType animalType;
    private String breed;
    private double weightLbs;
    private double pricePerLb;
    private String sourceFarm;
    private String description;
    private String imageUrl;
    private String zipCode;
    private ListingStatus status;
    private LocalDate processingDate;
    private LocalDateTime postedAt;
    private LocalDateTime fullyClaimedAt;

    private String farmerId;
    private String farmerName;
    private String farmerShopName;
    private String farmerBio;
    private String farmerCertifications;

    private List<CutDto> cuts;
    private int totalCuts;
    private int claimedCuts;

    @Data
    @Builder
    public static class CutDto {
        private Long id;
        private String label;
        private Double weightLbs;
        private boolean claimed;
        private String claimedByName;
        private LocalDateTime claimedAt;
    }
}
