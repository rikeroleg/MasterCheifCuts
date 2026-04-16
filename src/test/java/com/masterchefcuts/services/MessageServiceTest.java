package com.masterchefcuts.services;

import com.masterchefcuts.dto.MessageResponse;
import com.masterchefcuts.dto.MessageThreadResponse;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Message;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.MessageRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private ParticipantRepo participantRepo;

    @InjectMocks private MessageService messageService;

    private Participant sender;
    private Participant receiver;

    @BeforeEach
    void setUp() {
        sender = Participant.builder()
                .id("sender-1").firstName("Alice").lastName("Smith")
                .role(Role.BUYER).email("alice@test.com").password("pass")
                .street("1 St").city("City").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        receiver = Participant.builder()
                .id("receiver-1").firstName("Bob").lastName("Farmer")
                .role(Role.FARMER).email("bob@farm.com").password("pass")
                .street("2 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();
    }

    // ── send ─────────────────────────────────────────────────────────────────

    @Test
    void send_success_savesAndReturnsDto() {
        Message saved = Message.builder()
                .id(1L).sender(sender).receiver(receiver)
                .content("Hello!").read(false).sentAt(LocalDateTime.now()).build();

        when(participantRepo.findById("sender-1")).thenReturn(Optional.of(sender));
        when(participantRepo.findById("receiver-1")).thenReturn(Optional.of(receiver));
        when(messageRepository.save(any(Message.class))).thenReturn(saved);

        MessageResponse response = messageService.send("sender-1", "receiver-1", "Hello!");

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("Hello!");
        assertThat(response.getSenderName()).isEqualTo("Alice Smith");
    }

    @Test
    void send_stripsScriptTag_fromContent() {
        when(participantRepo.findById("sender-1")).thenReturn(Optional.of(sender));
        when(participantRepo.findById("receiver-1")).thenReturn(Optional.of(receiver));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        messageService.send("sender-1", "receiver-1", "<script>xss()</script>Hello");

        verify(messageRepository).save(argThat(m -> !m.getContent().contains("<script>")));
    }

    @Test
    void send_stripsAllHtmlTags_fromContent() {
        when(participantRepo.findById("sender-1")).thenReturn(Optional.of(sender));
        when(participantRepo.findById("receiver-1")).thenReturn(Optional.of(receiver));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        messageService.send("sender-1", "receiver-1", "<img src=x onerror=alert(1)>Safe text");

        verify(messageRepository).save(argThat(m -> m.getContent().equals("Safe text")));
    }

    @Test
    void send_throwsWhenContentIsBlank() {
        assertThatThrownBy(() -> messageService.send("sender-1", "receiver-1", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    void send_throwsWhenContentTooLong() {
        String longContent = "a".repeat(2001);
        assertThatThrownBy(() -> messageService.send("sender-1", "receiver-1", longContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too long");
    }

    @Test
    void send_throwsWhenSenderNotFound() {
        when(participantRepo.findById("sender-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.send("sender-1", "receiver-1", "Hello"))
                .hasMessageContaining("Sender not found");
    }

    @Test
    void send_throwsWhenReceiverNotFound() {
        when(participantRepo.findById("sender-1")).thenReturn(Optional.of(sender));
        when(participantRepo.findById("receiver-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.send("sender-1", "receiver-1", "Hello"))
                .hasMessageContaining("Recipient not found");
    }

    // ── getConversation ───────────────────────────────────────────────────────

    @Test
    void getConversation_returnsAllMessages() {
        Message msg = Message.builder()
                .id(1L).sender(sender).receiver(receiver)
                .content("Hi!").read(false).sentAt(LocalDateTime.now()).build();
        when(messageRepository.findConversation("sender-1", "receiver-1")).thenReturn(List.of(msg));

        List<MessageResponse> result = messageService.getConversation("sender-1", "receiver-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("Hi!");
        assertThat(result.get(0).getSenderId()).isEqualTo("sender-1");
    }

    @Test
    void getConversation_emptyHistory_returnsEmptyList() {
        when(messageRepository.findConversation("sender-1", "receiver-1")).thenReturn(List.of());

        List<MessageResponse> result = messageService.getConversation("sender-1", "receiver-1");

        assertThat(result).isEmpty();
    }

    // ── getThreads ────────────────────────────────────────────────────────────

    @Test
    void getThreads_buildsThreadFromLatestMessages() {
        Message msg = Message.builder()
                .id(1L).sender(receiver).receiver(sender)
                .content("Latest msg").read(false).sentAt(LocalDateTime.now()).build();
        when(messageRepository.findLatestPerThread("sender-1")).thenReturn(List.of(msg));
        when(messageRepository.countByReceiverIdAndSenderIdAndReadFalse("sender-1", "receiver-1")).thenReturn(2L);

        List<MessageThreadResponse> threads = messageService.getThreads("sender-1");

        assertThat(threads).hasSize(1);
        assertThat(threads.get(0).getOtherParticipantId()).isEqualTo("receiver-1");
        assertThat(threads.get(0).getUnreadCount()).isEqualTo(2L);
        assertThat(threads.get(0).getLastMessage()).isEqualTo("Latest msg");
    }

    @Test
    void getThreads_whenUserIsSender_otherIsReceiver() {
        Message msg = Message.builder()
                .id(2L).sender(sender).receiver(receiver)
                .content("My msg").read(true).sentAt(LocalDateTime.now()).build();
        when(messageRepository.findLatestPerThread("sender-1")).thenReturn(List.of(msg));
        when(messageRepository.countByReceiverIdAndSenderIdAndReadFalse("sender-1", "receiver-1")).thenReturn(0L);

        List<MessageThreadResponse> threads = messageService.getThreads("sender-1");

        assertThat(threads.get(0).getOtherParticipantId()).isEqualTo("receiver-1");
        assertThat(threads.get(0).getUnreadCount()).isEqualTo(0L);
    }

    // ── markRead ──────────────────────────────────────────────────────────────

    @Test
    void markRead_setsReadTrueAndSaves() {
        Message msg = Message.builder()
                .id(5L).sender(sender).receiver(receiver)
                .content("Hello").read(false).sentAt(LocalDateTime.now()).build();
        when(messageRepository.findByIdAndReceiverId(5L, "receiver-1")).thenReturn(Optional.of(msg));

        messageService.markRead(5L, "receiver-1");

        assertThat(msg.isRead()).isTrue();
        verify(messageRepository).save(msg);
    }

    @Test
    void markRead_messageNotFound_doesNotSave() {
        when(messageRepository.findByIdAndReceiverId(99L, "receiver-1")).thenReturn(Optional.empty());

        messageService.markRead(99L, "receiver-1");

        verify(messageRepository, never()).save(any());
    }
}
