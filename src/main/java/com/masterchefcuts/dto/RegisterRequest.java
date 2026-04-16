package com.masterchefcuts.dto;

import com.masterchefcuts.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}$",
        message = "Password must contain at least one uppercase letter, one number, and one special character"
    )
    private String password;

    private String phone;

    @NotNull
    private Role role;

    private String shopName;

    private String street;

    private String apt;

    private String city;

    @Size(min = 2, max = 2, message = "State must be a 2-letter code")
    private String state;

    private String zipCode;

    // Optional: referral code (the referring user's ID) from ?ref= query param
    private String referralCode;

    // Optional: farmer profile enrichment
    private String bio;
    private String certifications;
}
