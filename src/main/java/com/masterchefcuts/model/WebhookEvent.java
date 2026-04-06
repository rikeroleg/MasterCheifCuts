package com.masterchefcuts.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tracks processed Stripe webhook events to ensure idempotency.
 * Each Stripe event ID should only be processed once.
 */
@Entity
@Table(name = "webhook_events", indexes = {
    @Index(name = "idx_webhook_event_id", columnList = "stripeEventId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_event_id", nullable = false, unique = true, length = 100)
    private String stripeEventId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "processed_at")
    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();

    @Column(name = "payment_intent_id", length = 100)
    private String paymentIntentId;

    @Column(length = 500)
    private String notes;
}
