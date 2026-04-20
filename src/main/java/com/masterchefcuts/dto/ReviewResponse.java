package com.masterchefcuts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewResponse {
    private Long id;
    private Long listingId;
    private String buyerName;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;
    // Populated for /api/reviews/featured — used by homepage testimonials
    private String animalType;
    private String farmerShopName;
    private boolean featured;
}
