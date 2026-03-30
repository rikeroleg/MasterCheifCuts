package com.masterchefcuts.dto;

import lombok.Data;

@Data
public class PaymentIntentRequest {
    private Long listingId;
    private String cutLabel;
}
