package com.masterchefcuts.services;

import com.masterchefcuts.dto.MessageResponse;
import com.masterchefcuts.dto.MessageThreadResponse;
import com.masterchefcuts.model.Message;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.MessageRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ParticipantRepo participantRepo;

    @Transactional(readOnly = true)
    public List<MessageResponse> getConversation(String userId, String otherId) {
        return messageRepository.findConversation(userId, otherId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public MessageResponse send(String senderId, String receiverId, String content) {
        if (content == null || content.isBlank())
            throw new IllegalArgumentException("Message content cannot be empty");
        if (content.length() > 2000)
            throw new IllegalArgumentException("Message too long (max 2000 characters)");

        Participant sender = participantRepo.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        Participant receiver = participantRepo.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        Message msg = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content(stripHtml(content.trim()))
                .build();

        return toDto(messageRepository.save(msg));
    }

    @Transactional
    public void markRead(Long messageId, String recipientId) {
        messageRepository.findByIdAndReceiverId(messageId, recipientId).ifPresent(m -> {
            m.setRead(true);
            messageRepository.save(m);
        });
    }

    @Transactional(readOnly = true)
    public List<MessageThreadResponse> getThreads(String userId) {
        return messageRepository.findLatestPerThread(userId)
                .stream().map(m -> toThreadDto(m, userId)).collect(Collectors.toList());
    }

    private String stripHtml(String input) {
        if (input == null) return "";
        return input.replaceAll("<[^>]*>", "").trim();
    }

    private MessageResponse toDto(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .senderId(m.getSender().getId())
                .senderName(m.getSender().getFirstName() + " " + m.getSender().getLastName())
                .receiverId(m.getReceiver().getId())
                .receiverName(m.getReceiver().getFirstName() + " " + m.getReceiver().getLastName())
                .content(m.getContent())
                .read(m.isRead())
                .sentAt(m.getSentAt())
                .build();
    }

    private MessageThreadResponse toThreadDto(Message m, String userId) {
        Participant other = m.getSender().getId().equals(userId) ? m.getReceiver() : m.getSender();
        long unread = messageRepository.countByReceiverIdAndSenderIdAndReadFalse(userId, other.getId());
        return MessageThreadResponse.builder()
                .otherParticipantId(other.getId())
                .otherParticipantName((other.getFirstName() + " " + other.getLastName()).trim())
                .lastMessage(m.getContent())
                .unreadCount(unread)
                .build();
    }
}
