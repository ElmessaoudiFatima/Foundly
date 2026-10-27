package com.backend.dto.notification;

import com.backend.enums.NotificationChannel;
import com.backend.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        String title,
        String body,
        NotificationChannel channel,
        NotificationType type,
        UUID dossierId,
        String dossierReference,
        boolean read,
        Instant createdAt,
        Instant readAt
) {}
