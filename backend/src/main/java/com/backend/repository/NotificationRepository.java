package com.backend.repository;

import com.backend.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByUtilisateurIdOrderByCreatedAtDesc(UUID utilisateurId, Pageable pageable);

    Page<NotificationEntity> findByUtilisateurIdAndIsReadOrderByCreatedAtDesc(UUID utilisateurId, boolean isRead, Pageable pageable);

    Optional<NotificationEntity> findByIdAndUtilisateurId(UUID id, UUID utilisateurId);

    long countByUtilisateurIdAndIsReadFalse(UUID utilisateurId);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true, n.readAt = :now WHERE n.utilisateur.id = :utilisateurId AND n.isRead = false")
    int markAllAsReadForUser(@Param("utilisateurId") UUID utilisateurId, @Param("now") Instant now);
}
