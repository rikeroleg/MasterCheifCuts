package com.masterchefcuts.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "referrals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "referrer_id")
    private String referrerId;

    @Column(nullable = false, unique = true, name = "referred_id")
    private String referredId;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
