package com.masterchefcuts.services;

import com.masterchefcuts.model.AuditEvent;
import com.masterchefcuts.repositories.AuditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditRepository auditRepository;

    @InjectMocks private AuditService auditService;

    // ── log ───────────────────────────────────────────────────────────────────

    @Test
    void log_validArgs_savesAuditEventWithCorrectFields() {
        when(auditRepository.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.log("admin-1", "APPROVE_USER", "user-42");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRepository).save(captor.capture());

        AuditEvent saved = captor.getValue();
        assertThat(saved.getActorId()).isEqualTo("admin-1");
        assertThat(saved.getAction()).isEqualTo("APPROVE_USER");
        assertThat(saved.getTargetId()).isEqualTo("user-42");
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void log_nullActorId_savesWithNullActor() {
        when(auditRepository.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.log(null, "CLOSE_LISTING", "listing-7");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRepository).save(captor.capture());

        assertThat(captor.getValue().getActorId()).isNull();
        assertThat(captor.getValue().getAction()).isEqualTo("CLOSE_LISTING");
        assertThat(captor.getValue().getTargetId()).isEqualTo("listing-7");
    }

    @Test
    void log_repositoryThrows_doesNotPropagateException() {
        when(auditRepository.save(any(AuditEvent.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        // Should swallow the exception and not rethrow it
        assertThatCode(() -> auditService.log("admin-1", "DELETE_USER", "user-99"))
                .doesNotThrowAnyException();
    }
}
