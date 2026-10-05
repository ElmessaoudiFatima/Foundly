package com.backend.dto.response;

/**
 * Réponse de session authentifiée — alignée sur le schéma OpenAPI "AuthSession".
 * Champs : accessToken, refreshToken, tokenType, expiresIn, user.
 */
public record AuthSessionResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,   // durée de vie du JWT d'accès en secondes
        UserResponse user
) {}
