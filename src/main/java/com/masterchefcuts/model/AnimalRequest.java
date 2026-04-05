package com.masterchefcuts.model;

import com.masterchefcuts.enums.AnimalRequestStatus;
import com.masterchefcuts.enums.AnimalType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "animal_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnimalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Participant buyer;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AnimalType animalType;

    @Column(nullable = false)
    private String breed;

    private String description;

    @Column(nullable = false)
    private String zipCode;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "animal_request_cuts", joinColumns = @JoinColumn(name = "request_id"))
    @Column(name = "cut_label")
    @Builder.Default
    private List<String> cutLabels = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AnimalRequestStatus status = AnimalRequestStatus.OPEN;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fulfilled_by_farmer_id")
    private Participant fulfilledByFarmer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fulfilled_listing_id")
    private Listing fulfilledListing;
}
