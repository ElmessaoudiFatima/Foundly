package com.backend.service.impl;

import com.backend.dto.notification.NotificationDto;
import com.backend.dto.notification.PagedNotificationDto;
import com.backend.entity.Dossier;
import com.backend.entity.NotificationEntity;
import com.backend.entity.Utilisateur;
import com.backend.enums.NotificationChannel;
import com.backend.enums.NotificationType;
import java.util.NoSuchElementException;
import com.backend.repository.NotificationRepository;
import com.backend.service.EmailService;
import com.backend.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    public NotificationServiceImpl(NotificationRepository notificationRepository, EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public NotificationEntity sendNotification(
            Utilisateur destinataire,
            Dossier dossier,
            String titre,
            String message,
            NotificationChannel channel,
            NotificationType type
    ) {
        // Enregistrer la notification in-app dans tous les cas pour la visibilité de l'utilisateur
        NotificationEntity notif = new NotificationEntity();
        notif.setUtilisateur(destinataire);
        notif.setDossier(dossier);
        notif.setTitre(titre);
        notif.setBody(message);
        notif.setChannel(channel != null ? channel : NotificationChannel.IN_APP);
        notif.setType(type != null ? type : NotificationType.INFO);
        notif.setRead(false);
        notif.setCreatedAt(Instant.now());

        NotificationEntity saved = notificationRepository.save(notif);

        // Si EMAIL ou événement important, envoyer aussi par e-mail
        if (destinataire.getEmail() != null && !destinataire.getEmail().isBlank()) {
            try {
                String dossierRef = dossier != null ? dossier.getReference() : "Général";
                String actionUrl = dossier != null 
                        ? frontendUrl.replaceAll("/+$", "") + "/dashboard/dossiers/" + dossier.getId()
                        : frontendUrl.replaceAll("/+$", "") + "/dashboard";
                String recipientName = destinataire.getPrenom() + " " + destinataire.getNom();
                emailService.sendDossierNotificationEmail(destinataire.getEmail(), recipientName, titre, message, dossierRef, actionUrl);
            } catch (Exception e) {
                log.warn("Impossible d'envoyer l'email de notification à {} : {}", destinataire.getEmail(), e.getMessage());
            }
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedNotificationDto getUserNotifications(UUID utilisateurId, Boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<NotificationEntity> notifPage;

        if (Boolean.TRUE.equals(unreadOnly)) {
            notifPage = notificationRepository.findByUtilisateurIdAndIsReadOrderByCreatedAtDesc(utilisateurId, false, pageable);
        } else {
            notifPage = notificationRepository.findByUtilisateurIdOrderByCreatedAtDesc(utilisateurId, pageable);
        }

        long unreadCount = notificationRepository.countByUtilisateurIdAndIsReadFalse(utilisateurId);

        List<NotificationDto> items = notifPage.getContent().stream()
                .map(this::mapToDto)
                .toList();

        return new PagedNotificationDto(
                items,
                notifPage.getTotalElements(),
                notifPage.getTotalPages(),
                notifPage.getNumber(),
                notifPage.getSize(),
                unreadCount
        );
    }

    @Override
    @Transactional
    public NotificationDto markAsRead(UUID notificationId, UUID utilisateurId) {
        NotificationEntity notif = notificationRepository.findByIdAndUtilisateurId(notificationId, utilisateurId)
                .orElseThrow(() -> new NoSuchElementException("Notification introuvable : " + notificationId));

        if (!notif.isRead()) {
            notif.setRead(true);
            notif.setReadAt(Instant.now());
            notif = notificationRepository.save(notif);
        }

        return mapToDto(notif);
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID utilisateurId) {
        notificationRepository.markAllAsReadForUser(utilisateurId, Instant.now());
    }

    private NotificationDto mapToDto(NotificationEntity n) {
        return new NotificationDto(
                n.getId(),
                n.getTitre(),
                n.getBody(),
                n.getChannel(),
                n.getType(),
                n.getDossier() != null ? n.getDossier().getId() : null,
                n.getDossier() != null ? n.getDossier().getReference() : null,
                n.isRead(),
                n.getCreatedAt(),
                n.getReadAt()
        );
    }
}
