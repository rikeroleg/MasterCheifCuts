package com.masterchefcuts.dto;

import lombok.Data;

@Data
public class CartPaymentIntentRequest {
    private long amountCents;
    private String description;
}
