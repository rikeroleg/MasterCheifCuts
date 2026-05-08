package com.masterchefcuts.dto;

import com.masterchefcuts.enums.EmailPreference;
import com.masterchefcuts.enums.NotificationPreference;
import com.masterchefcuts.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String refreshToken;
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private String shopName;
    private String street;
    private String apt;
    private String city;
    private String state;
    private String zipCode;
    private boolean approved;
    private NotificationPreference notificationPreference;
    private EmailPreference emailPreference;
    private String bio;
    private String certifications;
    /** Epoch-ms when the access token expires. Used by the frontend refresh timer.
     *  Never present in /me or profile-update responses (token is null there). */
    private Long tokenExpiresAt;
}
