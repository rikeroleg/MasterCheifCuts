package com.masterchefcuts.dto;

import com.masterchefcuts.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {

    private Long id;
    private NotificationType type;
    private String icon;
    private String title;
    private String body;
    private boolean read;
    private Long listingId;
    private LocalDateTime createdAt;
}
