package com.backend.dto.response;

import com.backend.enums.RoleUtilisateur;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse utilisateur alignée sur le schéma OpenAPI "User".
 * Le CIN n'est JAMAIS renvoyé en clair : uniquement cinMasked.
 */
public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String cinMasked,   // ex: "AB***34"
        String phone,
        RoleUtilisateur role,
        boolean active,
        Instant createdAt
) {}
