package com.masterchefcuts.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ListingUpdateRequest {

    /** Updated breed (ACTIVE listings only; null = no change). */
    private String breed;

    /** Updated description (null = no change). */
    private String description;

    /** Updated price per lb (ACTIVE listings only; null = no change). */
    private Double pricePerLb;

    /** Updated / extended processing date (null = no change). */
    private LocalDate processingDate;
}
