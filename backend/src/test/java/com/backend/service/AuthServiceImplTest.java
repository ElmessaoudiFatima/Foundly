package com.backend.service;

import com.backend.dto.request.LoginRequest;
import com.backend.dto.request.RegisterRequest;
import com.backend.dto.response.AuthSessionResponse;
import com.backend.entity.Utilisateur;
import com.backend.enums.RoleUtilisateur;
import com.backend.exception.EmailAlreadyUsedException;
import com.backend.exception.InvalidCredentialsException;
import com.backend.mapper.UserMapper;
import com.backend.repository.UtilisateurRepository;
import com.backend.security.JwtService;
import com.backend.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires d'AuthServiceImpl — sans contexte Spring (JUnit 5 + Mockito).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private UserMapper userMapper;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                utilisateurRepository, passwordEncoder,
                jwtService, refreshTokenService, userMapper
        );
    }

    // -----------------------------------------------------------------------
    // register — OK
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("register : crée un compte avec mot de passe haché, rôle ENTREPRENEUR, email en minuscules")
    void register_success() {
        // Arrange
        RegisterRequest req = new RegisterRequest(
                "Ali", "Bencir", "Ali.BENCIR@GMAIL.COM",
                "MotDePasse123!", "AB123456", "0600000001"
        );

        when(utilisateurRepository.existsByEmail("ali.bencir@gmail.com")).thenReturn(false);

        Utilisateur mappedUser = new Utilisateur();
        mappedUser.setPrenom("Ali");
        mappedUser.setNom("Bencir");
        mappedUser.setEmail("ali.bencir@gmail.com");
        when(userMapper.toEntity(req)).thenReturn(mappedUser);

        when(passwordEncoder.encode("MotDePasse123!")).thenReturn("$2a$hashed");

        Utilisateur savedUser = new Utilisateur();
        savedUser.setId(UUID.randomUUID());
        savedUser.setPrenom("Ali");
        savedUser.setNom("Bencir");
        savedUser.setEmail("ali.bencir@gmail.com");
        savedUser.setPassword("$2a$hashed");
        savedUser.setRole(RoleUtilisateur.ENTREPRENEUR);
        savedUser.setActif(true);
        savedUser.setCreatedAt(Instant.now());
        when(utilisateurRepository.save(any(Utilisateur.class))).thenReturn(savedUser);

        when(jwtService.generateAccessToken(any())).thenReturn("access.jwt.token");
        when(jwtService.getAccessTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.create(any())).thenReturn("raw-refresh-token");
        when(userMapper.toResponse(any())).thenReturn(
                new com.backend.dto.response.UserResponse(
                        savedUser.getId(), "Ali", "Bencir",
                        "ali.bencir@gmail.com", "AB***56", "0600000001",
                        RoleUtilisateur.ENTREPRENEUR, true, savedUser.getCreatedAt()
                )
        );

        // Act
        AuthSessionResponse session = authService.register(req);

        // Assert — token type, expiresIn, accessToken présents
        assertThat(session.tokenType()).isEqualTo("Bearer");
        assertThat(session.expiresIn()).isEqualTo(900L);
        assertThat(session.accessToken()).isEqualTo("access.jwt.token");
        assertThat(session.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(session.user().role()).isEqualTo(RoleUtilisateur.ENTREPRENEUR);
        assertThat(session.user().email()).isEqualTo("ali.bencir@gmail.com");

        // Vérification : mot de passe haché, jamais le clair
        ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
        verify(utilisateurRepository).save(captor.capture());
        Utilisateur toSave = captor.getValue();
        assertThat(toSave.getPassword()).isEqualTo("$2a$hashed");
        assertThat(toSave.getPassword()).doesNotContain("MotDePasse123!");

        // Vérification : email normalisé en minuscules
        assertThat(toSave.getEmail()).isEqualTo("ali.bencir@gmail.com");

        // Vérification : rôle forcé ENTREPRENEUR
        assertThat(toSave.getRole()).isEqualTo(RoleUtilisateur.ENTREPRENEUR);

        // Vérification : compte actif
        assertThat(toSave.isActif()).isTrue();
    }

    // -----------------------------------------------------------------------
    // register — email déjà utilisé → 409
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("register : lève EmailAlreadyUsedException si l'email existe déjà")
    void register_emailAlreadyUsed() {
        RegisterRequest req = new RegisterRequest(
                "Test", "User", "existing@example.com",
                "MotDePasse123!", "CD654321", "0600000002"
        );

        when(utilisateurRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(utilisateurRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // login — OK
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("login : authentifie un utilisateur actif avec les bons identifiants")
    void login_success() {
        LoginRequest req = new LoginRequest("user@example.com", "MonMotDePasse123!");

        Utilisateur u = new Utilisateur();
        u.setId(UUID.randomUUID());
        u.setEmail("user@example.com");
        u.setPassword("$2a$hashed");
        u.setRole(RoleUtilisateur.ENTREPRENEUR);
        u.setActif(true);
        u.setCreatedAt(Instant.now());

        when(utilisateurRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("MonMotDePasse123!", "$2a$hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(u)).thenReturn("jwt.access.ok");
        when(jwtService.getAccessTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.create(u)).thenReturn("refresh-ok");
        when(userMapper.toResponse(u)).thenReturn(
                new com.backend.dto.response.UserResponse(
                        u.getId(), "User", "Test",
                        "user@example.com", "CD***21", "0600000000",
                        RoleUtilisateur.ENTREPRENEUR, true, u.getCreatedAt()
                )
        );

        AuthSessionResponse session = authService.login(req);

        assertThat(session.accessToken()).isEqualTo("jwt.access.ok");
        assertThat(session.refreshToken()).isEqualTo("refresh-ok");
        assertThat(session.user().active()).isTrue();
    }

    // -----------------------------------------------------------------------
    // login — mauvais mot de passe → 401 générique
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("login : lève InvalidCredentialsException si le mot de passe est incorrect")
    void login_wrongPassword() {
        LoginRequest req = new LoginRequest("user@example.com", "MauvaisMotDePasse!");

        Utilisateur u = new Utilisateur();
        u.setEmail("user@example.com");
        u.setPassword("$2a$correct_hash");
        u.setActif(true);

        when(utilisateurRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("MauvaisMotDePasse!", "$2a$correct_hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // -----------------------------------------------------------------------
    // login — compte inactif → 401 générique
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("login : lève InvalidCredentialsException si le compte est inactif")
    void login_inactiveAccount() {
        LoginRequest req = new LoginRequest("inactive@example.com", "MotDePasse123!");

        Utilisateur u = new Utilisateur();
        u.setEmail("inactive@example.com");
        u.setPassword("$2a$hashed");
        u.setActif(false);  // compte désactivé

        when(utilisateurRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class);

        // Vérification : on ne vérifie pas le mot de passe d'un compte inactif
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // -----------------------------------------------------------------------
    // login — email inconnu → 401 générique (même comportement)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("login : lève InvalidCredentialsException si l'email est inconnu")
    void login_unknownEmail() {
        LoginRequest req = new LoginRequest("nobody@example.com", "MotDePasse123!");

        when(utilisateurRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
