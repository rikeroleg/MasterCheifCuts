package com.masterchefcuts.enums;

public enum OrderStatus {
    PENDING_PAYMENT,   // Order created, awaiting payment
    DEPOSIT_PAID,      // 50% deposit paid (whole animal)
    PAID,              // Full payment received
    ACCEPTED,          // Farmer accepted the order
    PROCESSING,        // Farmer is processing (butchering/preparing)
    READY,             // Order ready for pickup/delivery
    COMPLETED,         // Buyer picked up / confirmed received
    SHIPPED,           // Used for deposit orders awaiting balance
    PAYMENT_FAILED     // Payment failed
}
