package com.backend.service;

import com.backend.dto.admin.AgentAdminResponse;
import com.backend.dto.admin.AgentCreateAdminRequest;
import com.backend.entity.Utilisateur;
import com.backend.enums.RoleUtilisateur;
import com.backend.repository.DossierRepository;
import com.backend.repository.UtilisateurRepository;
import com.backend.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private DossierRepository dossierRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AdminServiceImpl adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(
                dossierRepository,
                utilisateurRepository,
                passwordEncoder,
                emailService,
                refreshTokenService
        );
    }

    @Test
    @DisplayName("createAgent : génère automatiquement le mot de passe, l'encode et envoie un e-mail de bienvenue")
    void createAgent_autoGeneratesPassword_andSendsWelcomeEmail() {
        AgentCreateAdminRequest req = new AgentCreateAdminRequest(
                "Nadia",
                "El Fassi",
                "nadia.elfassi@ompic.ma",
                null, // Pas de mot de passe fourni -> génération automatique requise
                "OMPIC",
                "0661234567",
                "BE123456"
        );

        when(utilisateurRepository.existsByEmail("nadia.elfassi@ompic.ma")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$encodedPassword");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> {
            Utilisateur u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        AgentAdminResponse response = adminService.createAgent(req);

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("nadia.elfassi@ompic.ma");
        assertThat(response.fullName()).isEqualTo("Nadia El Fassi");
        assertThat(response.generatedPassword()).isNotBlank();
        assertThat(response.generatedPassword().length()).isGreaterThanOrEqualTo(12);

        // Vérification que le mot de passe encodé a été sauvegardé
        ArgumentCaptor<Utilisateur> userCaptor = ArgumentCaptor.forClass(Utilisateur.class);
        verify(utilisateurRepository).save(userCaptor.capture());
        Utilisateur savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(RoleUtilisateur.AGENT);
        assertThat(savedUser.isActif()).isTrue();
        assertThat(savedUser.getPassword()).isEqualTo("$2a$10$encodedPassword");

        // Vérification que l'e-mail a été envoyé avec les identifiants
        verify(emailService).sendAgentWelcomeEmail(
                eq("nadia.elfassi@ompic.ma"),
                eq("Nadia El Fassi"),
                eq(response.generatedPassword()),
                eq("OMPIC")
        );
    }

    @Test
    @DisplayName("updateAgentStatus : désactivation sans motif lance IllegalArgumentException")
    void updateAgentStatus_deactivateWithoutReason_throwsException() {
        UUID agentId = UUID.randomUUID();
        Utilisateur agent = new Utilisateur();
        agent.setId(agentId);
        agent.setRole(RoleUtilisateur.AGENT);
        agent.setActif(true);

        when(utilisateurRepository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> adminService.updateAgentStatus(agentId, false, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Le motif de désactivation est obligatoire");

        verify(utilisateurRepository, never()).saveAndFlush(any());
        verify(emailService, never()).sendAgentDeactivationEmail(any(), any(), any());
    }

    @Test
    @DisplayName("updateAgentStatus : désactivation avec motif révoque les tokens et envoie un e-mail à l'agent")
    void updateAgentStatus_deactivateWithReason_revokesTokens_andSendsEmail() {
        UUID agentId = UUID.randomUUID();
        Utilisateur agent = new Utilisateur();
        agent.setId(agentId);
        agent.setRole(RoleUtilisateur.AGENT);
        agent.setPrenom("Karim");
        agent.setNom("Berrada");
        agent.setEmail("karim.berrada@cri.ma");
        agent.setActif(true);

        when(utilisateurRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(utilisateurRepository.saveAndFlush(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentAdminResponse response = adminService.updateAgentStatus(agentId, false, "Fin de contrat de mission");

        assertThat(response).isNotNull();
        assertThat(response.active()).isFalse();

        // Vérification de la révocation des sessions
        verify(refreshTokenService).revokeAllByUser(agentId);

        // Vérification de l'e-mail de notification
        verify(emailService).sendAgentDeactivationEmail(
                eq("karim.berrada@cri.ma"),
                eq("Karim Berrada"),
                eq("Fin de contrat de mission")
        );
    }

    @Test
    @DisplayName("updateAgentStatus : activation réussie sans envoi d'e-mail de désactivation")
    void updateAgentStatus_activateSuccess() {
        UUID agentId = UUID.randomUUID();
        Utilisateur agent = new Utilisateur();
        agent.setId(agentId);
        agent.setRole(RoleUtilisateur.AGENT);
        agent.setPrenom("Karim");
        agent.setNom("Berrada");
        agent.setEmail("karim.berrada@cri.ma");
        agent.setActif(false);

        when(utilisateurRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(utilisateurRepository.saveAndFlush(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentAdminResponse response = adminService.updateAgentStatus(agentId, true, null);

        assertThat(response).isNotNull();
        assertThat(response.active()).isTrue();
        verify(refreshTokenService, never()).revokeAllByUser(any());
        verify(emailService, never()).sendAgentDeactivationEmail(any(), any(), any());
    }
}
