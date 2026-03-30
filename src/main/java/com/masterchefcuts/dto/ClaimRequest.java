package com.masterchefcuts.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ClaimRequest {

    @NotNull
    private Long cutId;

    private String paymentIntentId;
}
