package com.masterchefcuts.enums;

public enum OrderStatus {
    PENDING_PAYMENT,   // Order created, awaiting payment
    PAID,              // Full payment received
    ACCEPTED,          // Farmer accepted the order
    PROCESSING,        // Farmer is processing (butchering/preparing)
    READY,             // Order ready for pickup/delivery
    COMPLETED,         // Buyer picked up / confirmed received
    PAYMENT_FAILED,    // Payment failed
    REFUNDED,          // Full or partial refund issued via Stripe
    DISPUTED           // Stripe chargeback/dispute opened by buyer
}
