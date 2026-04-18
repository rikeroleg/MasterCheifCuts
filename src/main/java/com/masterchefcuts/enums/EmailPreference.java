package com.masterchefcuts.enums;

/**
 * Controls which transactional emails a participant receives.
 *
 * ALL         – every email (claims, processing dates, order confirmations, disputes…)
 * IMPORTANT   – order-critical only (order confirmed, dispute opened, password reset, verify email)
 * NONE        – no marketing / transactional emails (auth emails still sent for security)
 */
public enum EmailPreference {
    ALL,
    IMPORTANT,
    NONE
}
