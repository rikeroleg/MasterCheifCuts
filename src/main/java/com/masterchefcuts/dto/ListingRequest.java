package com.masterchefcuts.dto;

import com.masterchefcuts.enums.AnimalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class ListingRequest {

    @NotNull
    private AnimalType animalType;

    @NotBlank
    private String breed;

    @Positive
    private double weightLbs;

    @Positive
    private double pricePerLb;

    private String sourceFarm;
    private String description;

    @NotBlank
    private String zipCode;

    @NotNull
    private List<CutRequest> cuts;
}
