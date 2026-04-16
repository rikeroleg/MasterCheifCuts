package com.masterchefcuts.services;

import com.masterchefcuts.dto.NotificationPageResponse;
import com.masterchefcuts.dto.NotificationResponse;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Notification;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private NotificationService notificationService;

    private Participant recipient;
    private Notification notification;

    @BeforeEach
    void setUp() {
        recipient = Participant.builder()
                .id("user-1").firstName("Alice").lastName("Smith")
                .role(Role.BUYER).email("alice@example.com").password("pass")
                .street("1 Main St").city("Town").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        notification = Notification.builder()
                .id(1L).recipient(recipient)
                .type(NotificationType.CUT_CLAIMED)
                .icon("🛒").title("Cut claimed").body("You claimed Ribeye")
                .listingId(1L).read(false).createdAt(LocalDateTime.now()).build();
    }

    // ── send ──────────────────────────────────────────────────────────────────

    @Test
    void send_savesNotificationWithCorrectFields() {
        notificationService.send(recipient, NotificationType.CUT_CLAIMED,
                "🛒", "Cut claimed", "You claimed Ribeye", 1L);

        verify(notificationRepository).save(argThat(n ->
                n.getRecipient().equals(recipient) &&
                n.getType() == NotificationType.CUT_CLAIMED &&
                n.getTitle().equals("Cut claimed") &&
                n.getListingId().equals(1L)
        ));
    }

    // ── getForRecipient ───────────────────────────────────────────────────────

    @Test
    void getForRecipient_returnsMappedDtos() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(notification));

        List<NotificationResponse> result = notificationService.getForRecipient("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getTitle()).isEqualTo("Cut claimed");
        assertThat(result.get(0).isRead()).isFalse();
        assertThat(result.get(0).getListingId()).isEqualTo(1L);
    }

    @Test
    void getForRecipient_returnsEmptyListWhenNone() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of());

        assertThat(notificationService.getForRecipient("user-1")).isEmpty();
    }

    // ── getUnreadCount ────────────────────────────────────────────────────────

    @Test
    void getUnreadCount_returnsCorrectCount() {
        when(notificationRepository.countByRecipientIdAndReadFalse("user-1")).thenReturn(3L);

        assertThat(notificationService.getUnreadCount("user-1")).isEqualTo(3L);
    }

    // ── markRead ──────────────────────────────────────────────────────────────

    @Test
    void markRead_setsReadTrueAndSaves_whenRecipientMatches() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        notificationService.markRead(1L, "user-1");

        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_doesNotSave_whenRecipientDoesNotMatch() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        notificationService.markRead(1L, "other-user");

        assertThat(notification.isRead()).isFalse();
        verify(notificationRepository, never()).save(notification);
    }

    @Test
    void markRead_doesNothing_whenNotificationNotFound() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        notificationService.markRead(99L, "user-1");

        verify(notificationRepository, never()).save(any());
    }

    // ── markAllRead ───────────────────────────────────────────────────────────

    @Test
    void markAllRead_callsRepositoryMarkAll() {
        notificationService.markAllRead("user-1");

        verify(notificationRepository).markAllReadByRecipientId("user-1");
    }

    // ── clearAll ──────────────────────────────────────────────────────────────

    @Test
    void clearAll_deletesAllNotificationsForRecipient() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(notification));

        notificationService.clearAll("user-1");

        verify(notificationRepository).deleteAll(List.of(notification));
    }

    @Test
    void clearAll_doesNotThrowWhenNoNotifications() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of());

        assertThatCode(() -> notificationService.clearAll("user-1"))
                .doesNotThrowAnyException();
    }

    // ── subscribe (SSE) ───────────────────────────────────────────────────

    @Test
    void subscribe_returnsNonNullSseEmitter() {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                notificationService.subscribe("user-1");

        assertThat(emitter).isNotNull();
    }

    @Test
    void subscribe_calledTwice_secondCallWins() {
        // Two subscriptions for the same user — the second replaces the first
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter first  = notificationService.subscribe("user-1");
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter second = notificationService.subscribe("user-1");

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
    }

    // ── getForRecipientPaged ──────────────────────────────────────────────────

    @Test
    void getForRecipientPaged_returnsCorrectPageResponse() {
        Page<Notification> page = new PageImpl<>(List.of(notification), PageRequest.of(0, 20), 1);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(eq("user-1"), any()))
                .thenReturn(page);
        when(notificationRepository.countByRecipientIdAndReadFalse("user-1")).thenReturn(2L);

        NotificationPageResponse result = notificationService.getForRecipientPaged("user-1", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Cut claimed");
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getUnreadCount()).isEqualTo(2L);
        assertThat(result.isHasNext()).isFalse();
    }

    @Test
    void getForRecipientPaged_emptyPage_returnsEmptyContent() {
        Page<Notification> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(eq("user-1"), any()))
                .thenReturn(emptyPage);
        when(notificationRepository.countByRecipientIdAndReadFalse("user-1")).thenReturn(0L);

        NotificationPageResponse result = notificationService.getForRecipientPaged("user-1", 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getUnreadCount()).isEqualTo(0L);
    }
}
