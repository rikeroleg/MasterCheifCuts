package com.masterchefcuts.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DisputeRequest {

    private Long claimId;

    private Long listingId;

    @NotBlank
    private String type;

    @NotBlank
    private String description;
}
