package com.masterchefcuts.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable record of a significant system action for audit purposes.
 */
@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_actor",  columnList = "actorId"),
        @Index(name = "idx_audit_target", columnList = "targetId"),
        @Index(name = "idx_audit_ts",     columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user (participant ID) who performed the action; null for system actions. */
    @Column(name = "actor_id")
    private String actorId;

    /** Short, ALL_CAPS action name, e.g. APPROVE_USER, CLOSE_LISTING. */
    @Column(nullable = false, length = 100)
    private String action;

    /** The primary entity affected (user ID, listing ID, order ID). */
    @Column(name = "target_id")
    private String targetId;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
