package com.backend.service.impl;

import com.backend.config.JwtProperties;
import com.backend.entity.RefreshToken;
import com.backend.entity.Utilisateur;
import com.backend.exception.InvalidRefreshTokenException;
import com.backend.repository.RefreshTokenRepository;
import com.backend.service.RefreshTokenService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Implémentation du service de gestion des refresh tokens opaques.
 * - Génération : SecureRandom 32 octets → Base64 URL-safe.
 * - Stockage   : uniquement le hash SHA-256 du token brut.
 * - Rotation   : chaque refresh révoque l'ancien et crée un nouveau token.
 */
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom;

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository,
                                   JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.secureRandom = new SecureRandom();
    }

    @Override
    @Transactional
    public String create(Utilisateur utilisateur) {
        // Génération d'un token opaque aléatoire (32 octets → Base64 URL-safe sans padding)
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // Calcul du hash SHA-256 pour le stockage
        String tokenHash = sha256(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUtilisateur(utilisateur);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(Instant.now().plusSeconds(
                jwtProperties.getRefreshTtlDays() * 24 * 3600
        ));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        // Retourner la valeur brute (envoyée au client une seule fois)
        return rawToken;
    }

    @Override
    public RefreshToken validateAndGet(String rawToken) {
        String tokenHash = sha256(rawToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (token.isRevoked()) {
            throw new InvalidRefreshTokenException();
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }
        if (!token.getUtilisateur().isActif()) {
            throw new InvalidRefreshTokenException();
        }

        return token;
    }

    @Override
    @Transactional
    public void revoke(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    @Override
    @Transactional
    public void revokeAllByUser(UUID userId) {
        refreshTokenRepository.revokeAllActiveByUtilisateurId(userId);
    }

    /**
     * Calcule le hash SHA-256 d'une chaîne UTF-8 et retourne la représentation HEX.
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti présent dans tout JVM standard
            throw new RuntimeException("SHA-256 non disponible", e);
        }
    }
}
