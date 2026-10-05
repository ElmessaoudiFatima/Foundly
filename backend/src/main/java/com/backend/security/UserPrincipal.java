package com.backend.security;

import java.util.UUID;

/**
 * Représente le principal authentifié dans le SecurityContext.
 * Immutable record portant les données minimales nécessaires à l'autorisation.
 */
public record UserPrincipal(
        UUID id,
        String email,
        String role,
        boolean actif
) {}
