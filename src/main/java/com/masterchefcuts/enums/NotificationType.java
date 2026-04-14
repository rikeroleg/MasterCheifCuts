package com.masterchefcuts.enums;

public enum NotificationType {
    CUT_CLAIMED,
    LISTING_FULL,
    PROCESSING_SET,
    COMPLETE,
    REQUEST_FULFILLED,
    // Order lifecycle notifications
    ORDER_PAID,           // Notify farmer when buyer pays
    ORDER_ACCEPTED,       // Notify buyer when farmer accepts
    ORDER_PROCESSING,     // Notify buyer when farmer starts processing
    ORDER_READY,          // Notify buyer when order is ready
    ORDER_COMPLETED,      // Notify farmer when buyer confirms receipt
    ORDER_REFUNDED,        // Notify buyer when a refund is issued
    LISTING_CLOSED         // Notify buyer/waitlist when farmer closes a listing early
}
