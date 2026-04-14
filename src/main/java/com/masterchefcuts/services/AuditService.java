package com.masterchefcuts.services;

import com.masterchefcuts.model.AuditEvent;
import com.masterchefcuts.repositories.AuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRepository auditRepository;

    /**
     * Persists an audit event asynchronously so it never blocks the calling request.
     *
     * @param actorId  ID of the authenticated user performing the action (null for system)
     * @param action   ALL_CAPS action label, e.g. APPROVE_USER, CLOSE_LISTING
     * @param targetId ID of the affected entity (user, listing, order, etc.)
     */
    @Async
    public void log(String actorId, String action, String targetId) {
        try {
            auditRepository.save(AuditEvent.builder()
                    .actorId(actorId)
                    .action(action)
                    .targetId(targetId)
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("Failed to write audit event action={} actor={} target={}: {}",
                    action, actorId, targetId, e.getMessage());
        }
    }
}
