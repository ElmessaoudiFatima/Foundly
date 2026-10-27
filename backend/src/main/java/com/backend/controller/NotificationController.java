package com.backend.controller;

import com.backend.dto.notification.NotificationDto;
import com.backend.dto.notification.PagedNotificationDto;
import com.backend.entity.Utilisateur;
import com.backend.repository.UtilisateurRepository;
import com.backend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UtilisateurRepository utilisateurRepository;

    public NotificationController(
            NotificationService notificationService,
            UtilisateurRepository utilisateurRepository
    ) {
        this.notificationService = notificationService;
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * GET /notifications
     * Lister les notifications de l'utilisateur connecté
     */
    @GetMapping
    public ResponseEntity<PagedNotificationDto> getNotifications(
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        PagedNotificationDto res = notificationService.getUserNotifications(user.getId(), unreadOnly, page, size);
        return ResponseEntity.ok(res);
    }

    /**
     * PATCH /notifications/{notificationId}/read
     * Marquer une notification comme lue
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationDto> markAsRead(
            @PathVariable UUID notificationId,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        NotificationDto res = notificationService.markAsRead(notificationId, user.getId());
        return ResponseEntity.ok(res);
    }

    /**
     * PATCH /notifications/read-all
     * Tout marquer comme lu
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        Utilisateur user = getAuthenticatedUser(authentication);
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.noContent().build();
    }

    private Utilisateur getAuthenticatedUser(Authentication authentication) {
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal.getUsername());
        return utilisateurRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur authentifié introuvable : " + userId));
    }
}
