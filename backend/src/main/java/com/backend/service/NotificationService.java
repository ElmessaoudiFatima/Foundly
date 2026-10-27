package com.backend.service;

import com.backend.dto.notification.NotificationDto;
import com.backend.dto.notification.PagedNotificationDto;
import com.backend.entity.Dossier;
import com.backend.entity.NotificationEntity;
import com.backend.entity.Utilisateur;
import com.backend.enums.NotificationChannel;
import com.backend.enums.NotificationType;

import java.util.UUID;

public interface NotificationService {

    NotificationEntity sendNotification(
            Utilisateur destinataire,
            Dossier dossier,
            String titre,
            String message,
            NotificationChannel channel,
            NotificationType type
    );

    PagedNotificationDto getUserNotifications(UUID utilisateurId, Boolean unreadOnly, int page, int size);

    NotificationDto markAsRead(UUID notificationId, UUID utilisateurId);

    void markAllAsRead(UUID utilisateurId);
}
