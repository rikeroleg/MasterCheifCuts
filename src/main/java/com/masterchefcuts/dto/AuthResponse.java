package com.masterchefcuts.dto;

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
}
