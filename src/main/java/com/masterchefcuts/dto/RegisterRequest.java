package com.masterchefcuts.dto;

import com.masterchefcuts.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private String phone;

    @NotNull
    private Role role;

    private String shopName;

    @NotBlank(message = "Street address is required")
    private String street;

    private String apt;

    @NotBlank
    private String city;

    @NotBlank
    @Size(min = 2, max = 2, message = "State must be a 2-letter code")
    private String state;

    @NotBlank
    private String zipCode;
}
