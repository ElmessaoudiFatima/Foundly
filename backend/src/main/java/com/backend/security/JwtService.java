package com.backend.security;

import com.backend.config.JwtProperties;
import com.backend.entity.Utilisateur;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Service JWT : génération et validation des access tokens.
 * Utilise JJWT 0.12.x (io.jsonwebtoken).
 */
@Component
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // Dérivation de la clé HMAC-SHA à partir du secret configuré
        this.signingKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Génère un JWT d'accès.
     * Claims : sub = userId, email, role.
     */
    public String generateAccessToken(Utilisateur utilisateur) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getAccessTtlSeconds());

        return Jwts.builder()
                .subject(utilisateur.getId().toString())
                .claim("email", utilisateur.getEmail())
                .claim("role", utilisateur.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extrait tous les claims d'un token JWT (valide et non expiré).
     * Lève JwtException si le token est invalide ou expiré.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Valide le token et retourne true s'il est valide, false sinon.
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extrait le sujet (userId) du token.
     */
    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Retourne la durée de vie en secondes du JWT d'accès (pour expiresIn dans AuthSession).
     */
    public long getAccessTtlSeconds() {
        return jwtProperties.getAccessTtlSeconds();
    }
}
