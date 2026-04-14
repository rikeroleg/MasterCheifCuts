package com.masterchefcuts.services;

import com.masterchefcuts.dto.NotificationPageResponse;
import com.masterchefcuts.dto.NotificationResponse;
import com.masterchefcuts.enums.NotificationPreference;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.model.Notification;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // SSE emitters keyed by participantId. One active connection per user (last wins).
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void send(Participant recipient, NotificationType type, String icon, String title, String body, Long listingId) {
        send(recipient, type, icon, title, body, listingId, null);
    }

    public void send(Participant recipient, NotificationType type, String icon, String title, String body, Long listingId, String orderId) {
        // Respect notification preference: IMPORTANT_ONLY skips non-critical types
        if (recipient.getNotificationPreference() == NotificationPreference.IMPORTANT_ONLY) {
            // Order notifications and listing-full are always important
            if (type == NotificationType.CUT_CLAIMED || type == NotificationType.REQUEST_FULFILLED) {
                return;
            }
        }
        Notification n = Notification.builder()
                .recipient(recipient)
                .type(type)
                .icon(icon)
                .title(title)
                .body(body)
                .listingId(listingId)
                .orderId(orderId)
                .build();
        notificationRepository.save(n);
        pushToEmitter(recipient.getId(), toDto(n));
    }

    private void pushToEmitter(String recipientId, NotificationResponse dto) {
        SseEmitter emitter = emitters.get(recipientId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().data(dto));
        } catch (IOException e) {
            emitters.remove(recipientId, emitter);
            log.debug("SSE push failed for {}, emitter removed", recipientId);
        }
    }

    public SseEmitter subscribe(String participantId) {
        // Use Long.MAX_VALUE timeout — browser will reconnect when the connection drops.
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(participantId, emitter);

        Runnable cleanup = () -> emitters.remove(participantId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitters.remove(participantId, emitter);
        }
        return emitter;
    }

    public List<NotificationResponse> getForRecipient(String recipientId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public NotificationPageResponse getForRecipientPaged(String recipientId, int page, int size) {
        Page<Notification> pageResult = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                recipientId, PageRequest.of(page, size));
        long unread = notificationRepository.countByRecipientIdAndReadFalse(recipientId);

        return NotificationPageResponse.builder()
                .content(pageResult.getContent().stream().map(this::toDto).collect(Collectors.toList()))
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .hasNext(pageResult.hasNext())
                .unreadCount(unread)
                .build();
    }

    public long getUnreadCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Transactional
    public void markRead(Long notificationId, String recipientId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getRecipient().getId().equals(recipientId)) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    @Transactional
    public void markAllRead(String recipientId) {
        notificationRepository.markAllReadByRecipientId(recipientId);
    }

    @Transactional
    public void clearAll(String recipientId) {
        List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        notificationRepository.deleteAll(notifications);
    }

    private NotificationResponse toDto(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .icon(n.getIcon())
                .title(n.getTitle())
                .body(n.getBody())
                .read(n.isRead())
                .listingId(n.getListingId())
                .orderId(n.getOrderId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
