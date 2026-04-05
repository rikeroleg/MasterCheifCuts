package com.masterchefcuts.dto;

import com.masterchefcuts.enums.AnimalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AnimalRequestRequest {

    @NotNull
    private AnimalType animalType;

    @NotBlank
    private String breed;

    private String description;

    @NotBlank
    private String zipCode;

    @NotNull
    private List<String> cutLabels;
}
