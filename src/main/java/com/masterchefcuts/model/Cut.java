package com.masterchefcuts.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cuts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(nullable = false)
    private String label;

    @Builder.Default
    private boolean claimed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_by_id")
    private Participant claimedBy;

    private LocalDateTime claimedAt;
}
