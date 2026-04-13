package com.masterchefcuts.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageThreadResponse {
    private String otherParticipantId;
    private String otherParticipantName;
    private String lastMessage;
    private long unreadCount;
}