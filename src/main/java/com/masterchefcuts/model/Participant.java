package com.masterchefcuts.model;

import com.masterchefcuts.enums.NotificationPreference;
import com.masterchefcuts.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participant {

    @Id
    @UuidGenerator
    @Column(name = "id", unique = true, updatable = false)
    private String id;

    private String firstName;
    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String shopName;

    private String street;
    private String apt;
    private String city;
    private String state;
    private String zipCode;

    private String status;
    private double totalSpent;

    @Builder.Default
    private boolean approved = true;

    private String resetToken;
    private java.time.LocalDateTime resetTokenExpiry;

    private boolean emailVerified;
    private String verificationToken;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationPreference notificationPreference = NotificationPreference.ALL;

    // Farmer profile enrichment
    @Column(length = 500)
    private String bio;

    @Column(length = 500)
    private String certifications;

    // Stripe Connect — farmer payout account
    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    @Builder.Default
    @Column(name = "stripe_onboarding_complete")
    private Boolean stripeOnboardingComplete = false;
}
