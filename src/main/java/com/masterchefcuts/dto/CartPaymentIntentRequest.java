package com.masterchefcuts.dto;

import lombok.Data;
import java.util.List;

@Data
public class CartPaymentIntentRequest {
    private List<Long> cutIds;
    private String paymentType; // FULL or DEPOSIT
}
