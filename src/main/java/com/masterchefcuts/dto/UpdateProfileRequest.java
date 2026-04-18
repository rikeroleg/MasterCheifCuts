package com.masterchefcuts.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Size(max = 200)
    private String shopName;

    @Size(max = 200)
    private String street;

    @Size(max = 50)
    private String apt;

    @Size(max = 100)
    private String city;

    @Size(max = 2)
    private String state;

    @Size(max = 10)
    private String zipCode;

    @Size(max = 30)
    private String phone;

    @Size(max = 500)
    private String bio;

    @Size(max = 500)
    private String certifications;
}
