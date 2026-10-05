package com.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés JWT lues depuis application.properties (préfixe "jwt").
 * ATTENTION : jwt.secret doit être remplacé par une valeur sécurisée en production
 * (variable d'environnement JWT_SECRET).
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long accessTtlSeconds = 900;   // 15 minutes
    private long refreshTtlDays   = 7;

    public JwtProperties() {}

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getAccessTtlSeconds() { return accessTtlSeconds; }
    public void setAccessTtlSeconds(long accessTtlSeconds) { this.accessTtlSeconds = accessTtlSeconds; }

    public long getRefreshTtlDays() { return refreshTtlDays; }
    public void setRefreshTtlDays(long refreshTtlDays) { this.refreshTtlDays = refreshTtlDays; }
}
