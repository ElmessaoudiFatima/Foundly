package com.backend.service;

import com.backend.dto.request.LoginRequest;
import com.backend.dto.request.RefreshRequest;
import com.backend.dto.request.RegisterRequest;
import com.backend.dto.response.AuthSessionResponse;

import java.util.UUID;

/**
 * Contrat du service d'authentification.
 * Consommé uniquement par AuthController.
 */
public interface AuthService {

    AuthSessionResponse register(RegisterRequest request);

    AuthSessionResponse login(LoginRequest request);

    /**
     * Renouvellement de session via le refresh token opaque brut.
     */
    AuthSessionResponse refresh(String rawRefreshToken);

    /**
     * Déconnexion : révoque tous les refresh tokens actifs de l'utilisateur.
     */
    void logout(UUID userId);
}
