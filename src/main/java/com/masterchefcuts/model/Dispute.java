package com.masterchefcuts.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id")
    private Long claimId;

    @Column(name = "listing_id")
    private Long listingId;

    @Column(nullable = false, name = "buyer_id")
    private String buyerId;

    @Column(name = "buyer_name")
    private String buyerName;

    @Column(name = "farmer_id")
    private String farmerId;

    @Column(name = "farmer_name")
    private String farmerName;

    @Column(nullable = false)
    private String type;

    @Column(length = 2000)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private String status = "OPEN";

    @Column(length = 2000)
    private String resolution;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
