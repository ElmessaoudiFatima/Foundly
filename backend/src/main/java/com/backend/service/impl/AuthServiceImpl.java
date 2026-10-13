package com.backend.service.impl;

import com.backend.dto.request.LoginRequest;
import com.backend.dto.request.RefreshRequest;
import com.backend.dto.request.RegisterRequest;
import com.backend.dto.response.AuthSessionResponse;
import com.backend.entity.RefreshToken;
import com.backend.entity.Utilisateur;
import com.backend.enums.RoleUtilisateur;
import com.backend.exception.EmailAlreadyUsedException;
import com.backend.exception.InvalidCredentialsException;
import com.backend.mapper.UserMapper;
import com.backend.repository.UtilisateurRepository;
import com.backend.security.JwtService;
import com.backend.service.AuthService;
import com.backend.service.RefreshTokenService;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implémentation de AuthService.
 * Architecture stricte : aucun Repository, aucune entité exposée en dehors de ce service.
 * Injection par constructeur uniquement.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;

    public AuthServiceImpl(UtilisateurRepository utilisateurRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           RefreshTokenService refreshTokenService,
                           UserMapper userMapper) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userMapper = userMapper;
    }

    /**
     * Inscription : uniquement le rôle ENTREPRENEUR, jamais lu depuis le client.
     */
    @Override
    @Transactional
    public AuthSessionResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();

        if (utilisateurRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();  // → 409
        }

        try {
            Utilisateur u = userMapper.toEntity(req);
            u.setEmail(email);
            u.setPassword(passwordEncoder.encode(req.password())); // jamais en clair
            u.setRole(RoleUtilisateur.ENTREPRENEUR);               // toujours forcé
            u.setActif(true);
            utilisateurRepository.save(u);
            return buildSession(u);
        } catch (DataIntegrityViolationException e) {
            // Race condition : deux inscriptions simultanées avec le même email
            throw new EmailAlreadyUsedException();  // → 409
        }
    }

    /**
     * Connexion : même erreur générique pour email inexistant, inactif ou mauvais mdp
     * (ne révèle pas si un email existe).
     */
    @Override
    @Transactional
    public AuthSessionResponse login(LoginRequest req) {
        Utilisateur u = utilisateurRepository
                .findByEmail(req.email().trim().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);  // email inconnu → 401

        if (!u.isActif()) {
            throw new InvalidCredentialsException("Ce compte a été désactivé par l'administration. La connexion est impossible.");
        }

        if (!passwordEncoder.matches(req.password(), u.getPassword())) {
            throw new InvalidCredentialsException();  // mauvais mot de passe → 401
        }

        return buildSession(u);
    }

    /**
     * Renouvellement de session avec rotation du refresh token.
     */
    @Override
    @Transactional
    public AuthSessionResponse refresh(String rawRefreshToken) {
        RefreshToken oldToken = refreshTokenService.validateAndGet(rawRefreshToken); // → 401 si invalide
        Utilisateur u = oldToken.getUtilisateur();

        // Rotation : révoquer l'ancien token
        refreshTokenService.revoke(oldToken);

        // Émettre une nouvelle paire
        return buildSession(u);
    }

    /**
     * Déconnexion : révoque tous les refresh tokens actifs de l'utilisateur.
     */
    @Override
    @Transactional
    public void logout(UUID userId) {
        refreshTokenService.revokeAllByUser(userId);
    }

    // -----------------------------------------------------------------------
    // Méthode interne : construit une AuthSessionResponse complète
    // -----------------------------------------------------------------------
    private AuthSessionResponse buildSession(Utilisateur u) {
        String accessToken  = jwtService.generateAccessToken(u);
        String refreshToken = refreshTokenService.create(u);
        long   expiresIn    = jwtService.getAccessTtlSeconds();
        return new AuthSessionResponse(accessToken, refreshToken, "Bearer", expiresIn,
                userMapper.toResponse(u));
    }
}