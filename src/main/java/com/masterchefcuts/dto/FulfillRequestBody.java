package com.masterchefcuts.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class FulfillRequestBody {

    @Positive
    private double weightLbs;

    @Positive
    private double pricePerLb;

    private String sourceFarm;
}
