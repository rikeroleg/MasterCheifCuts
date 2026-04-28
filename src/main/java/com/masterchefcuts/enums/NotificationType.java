package com.masterchefcuts.enums;

public enum NotificationType {
    CUT_CLAIMED,
    CLAIM_EXPIRED,
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
    LISTING_CLOSED,        // Notify buyer/waitlist when farmer closes a listing early
    DISPUTE_OPENED,        // Notify both buyer and farmer when a dispute is filed
    REVIEW_RECEIVED,       // Notify farmer when a buyer leaves a review
    NEW_LISTING_NEARBY     // Notify participants when a new listing is posted in their ZIP
}
