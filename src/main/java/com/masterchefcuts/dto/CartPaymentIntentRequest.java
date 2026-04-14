package com.masterchefcuts.dto;

import lombok.Data;
import java.util.List;

@Data
public class CartPaymentIntentRequest {
    private List<Long> cutIds;
}
