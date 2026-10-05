package com.backend.service;

import com.backend.entity.RefreshToken;
import com.backend.entity.Utilisateur;

/**
 * Contrat du service de gestion des refresh tokens opaques.
 */
public interface RefreshTokenService {

    /**
     * Génère un token opaque aléatoire (SecureRandom, 32 octets, Base64 URL-safe),
     * stocke UNIQUEMENT son hash SHA-256 en base, et retourne la valeur brute du token
     * (à envoyer au client une seule fois).
     */
    String create(Utilisateur utilisateur);

    /**
     * Retrouve un RefreshToken à partir de la valeur brute du token reçue du client.
     * Lève InvalidRefreshTokenException si introuvable, révoqué, expiré,
     * ou si l'utilisateur associé est inactif.
     */
    RefreshToken validateAndGet(String rawToken);

    /**
     * Révoque un token spécifique (ex. : après rotation).
     */
    void revoke(RefreshToken token);

    /**
     * Révoque tous les refresh tokens actifs d'un utilisateur (logout).
     */
    void revokeAllByUser(java.util.UUID userId);
}
