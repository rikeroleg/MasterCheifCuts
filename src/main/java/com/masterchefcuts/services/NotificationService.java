package com.masterchefcuts.services;

import com.masterchefcuts.dto.NotificationResponse;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.model.Notification;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void send(Participant recipient, NotificationType type, String icon, String title, String body, Long listingId) {
        Notification n = Notification.builder()
                .recipient(recipient)
                .type(type)
                .icon(icon)
                .title(title)
                .body(body)
                .listingId(listingId)
                .build();
        notificationRepository.save(n);
    }

    public List<NotificationResponse> getForRecipient(String recipientId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
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
                .createdAt(n.getCreatedAt())
                .build();
    }
}
