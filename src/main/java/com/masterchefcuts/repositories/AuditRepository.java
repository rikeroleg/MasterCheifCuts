package com.masterchefcuts.repositories;

import com.masterchefcuts.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByActorIdOrderByTimestampDesc(String actorId);

    List<AuditEvent> findByTargetIdOrderByTimestampDesc(String targetId);
}
