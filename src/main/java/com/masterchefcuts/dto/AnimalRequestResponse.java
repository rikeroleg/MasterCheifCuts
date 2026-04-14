package com.masterchefcuts.dto;

import com.masterchefcuts.enums.AnimalRequestStatus;
import com.masterchefcuts.enums.AnimalType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AnimalRequestResponse {

    private Long id;
    private AnimalType animalType;
    private String breed;
    private String description;
    private String zipCode;
    private List<String> cutLabels;
    private AnimalRequestStatus status;
    private LocalDateTime createdAt;

    private String buyerId;
    private String buyerName;
    private String buyerZip;

    private String fulfilledByFarmerId;
    private String fulfilledByFarmerName;
    private Long fulfilledListingId;
}
