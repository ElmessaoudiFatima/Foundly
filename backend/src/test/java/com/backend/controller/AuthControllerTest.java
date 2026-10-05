package com.backend.controller;

import com.backend.dto.request.LoginRequest;
import com.backend.dto.request.RegisterRequest;
import com.backend.dto.response.AuthSessionResponse;
import com.backend.dto.response.UserResponse;
import com.backend.enums.RoleUtilisateur;
import com.backend.exception.EmailAlreadyUsedException;
import com.backend.exception.GlobalExceptionHandler;
import com.backend.exception.InvalidCredentialsException;
import com.backend.security.JwtAuthenticationFilter;
import com.backend.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests @WebMvcTest pour AuthController — AuthService est mocké.
 * Exclut JwtAuthenticationFilter pour simplifier les tests de la couche Controller.
 */
@WebMvcTest(
    controllers = AuthController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = JwtAuthenticationFilter.class
    )
)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static final UserResponse SAMPLE_USER = new UserResponse(
            UUID.randomUUID(), "Ali", "Bencir",
            "ali@example.com", "AB***56", "0600000001",
            RoleUtilisateur.ENTREPRENEUR, true, Instant.now()
    );

    private static final AuthSessionResponse SAMPLE_SESSION = new AuthSessionResponse(
            "access.jwt.token", "raw-refresh-token",
            "Bearer", 900L, SAMPLE_USER
    );

    // -----------------------------------------------------------------------
    // POST /auth/register — 201 Created
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("register : 201 pour une requête valide")
    @WithMockUser
    void register_valid_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "Ali", "Bencir", "ali@example.com",
                "MotDePasse123!", "AB123456", "0600000001"
        );

        when(authService.register(any(RegisterRequest.class))).thenReturn(SAMPLE_SESSION);

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("access.jwt.token"))
                .andExpect(jsonPath("$.user.email").value("ali@example.com"))
                .andExpect(jsonPath("$.user.role").value("ENTREPRENEUR"));
    }

    // -----------------------------------------------------------------------
    // POST /auth/register — 422 si email invalide
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("register : 422 avec fieldErrors si email invalide")
    @WithMockUser
    void register_invalidEmail_returns422() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "Ali", "Bencir", "not-an-email",
                "MotDePasse123!", "AB123456", "0600000001"
        );

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("email")));
    }

    // -----------------------------------------------------------------------
    // POST /auth/register — 422 si mot de passe trop court (< 12 caractères)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("register : 422 avec fieldErrors si mot de passe < 12 caractères")
    @WithMockUser
    void register_shortPassword_returns422() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "Ali", "Bencir", "ali@example.com",
                "court",  // < 12 caractères
                "AB123456", "0600000001"
        );

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("password")));
    }

    // -----------------------------------------------------------------------
    // POST /auth/register — 409 si email déjà utilisé
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("register : 409 si email déjà utilisé")
    @WithMockUser
    void register_emailAlreadyUsed_returns409() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "Ali", "Bencir", "existing@example.com",
                "MotDePasse123!", "AB123456", "0600000001"
        );

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyUsedException());

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    // -----------------------------------------------------------------------
    // POST /auth/login — 401 si identifiants invalides
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("login : 401 si identifiants invalides")
    @WithMockUser
    void login_invalidCredentials_returns401() throws Exception {
        LoginRequest req = new LoginRequest("user@example.com", "MauvaisMotDePasse!");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
