package com.backend.controller;

import com.backend.config.SecurityConfig;
import com.backend.dto.workflow.*;
import com.backend.enums.LegalFormCode;
import com.backend.enums.WorkflowStatus;
import com.backend.exception.GlobalExceptionHandler;
import com.backend.exception.WorkflowVersionNotEditableException;
import com.backend.exception.WorkflowVersionNotFoundException;
import com.backend.security.JwtAuthenticationFilter;
import com.backend.service.WorkflowVersionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration du controller WorkflowVersionController.
 * Utilise @WebMvcTest pour tester la couche HTTP sans démarrer le contexte complet.
 */
@WebMvcTest(controllers = WorkflowVersionController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class WorkflowVersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkflowVersionService workflowVersionService;

    // MockBean nécessaires pour que le contexte de sécurité démarre correctement
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.doAnswer(invocation -> {
            jakarta.servlet.ServletRequest req = invocation.getArgument(0);
            jakarta.servlet.ServletResponse res = invocation.getArgument(1);
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Données de test
    // -----------------------------------------------------------------------
    private WorkflowVersionSummaryDto buildSummary() {
        WorkflowVersionSummaryDto dto = new WorkflowVersionSummaryDto();
        dto.setId(UUID.randomUUID());
        dto.setFormeJuridique("SARL");
        dto.setFormeJuridiqueLabel("SARL");
        dto.setNumeroVersion("1.0");
        dto.setStatut("ACTIVE");
        dto.setStatutLabel("Active");
        dto.setNombreEtapes(8);
        dto.setDateModification("2026-10-01T10:00:00Z");
        return dto;
    }

    private WorkflowVersionDetailDto buildDetail() {
        WorkflowVersionDetailDto dto = new WorkflowVersionDetailDto();
        dto.setId(UUID.randomUUID());
        dto.setFormeJuridique("SARL");
        dto.setFormeJuridiqueLabel("SARL");
        dto.setNumeroVersion("2.0");
        dto.setStatut("BROUILLON");
        dto.setStatutLabel("Brouillon");
        dto.setEtapes(List.of());
        dto.setDateModification("2026-10-28T08:00:00Z");
        dto.setDateCreation("2026-10-28T08:00:00Z");
        return dto;
    }

    // -----------------------------------------------------------------------
    // GET /admin/workflows → 200
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("GET /admin/workflows → 200 avec liste")
    @WithMockUser(roles = "ADMINISTRATEUR")
    void getAllVersions_shouldReturn200() throws Exception {
        when(workflowVersionService.getAllVersions(null))
                .thenReturn(List.of(buildSummary()));

        mockMvc.perform(get("/admin/workflows"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].formeJuridique").value("SARL"))
                .andExpect(jsonPath("$[0].statut").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /admin/workflows?formeJuridique=SARL → 200 avec filtre")
    @WithMockUser(roles = "ADMINISTRATEUR")
    void getAllVersions_withFilter_shouldReturn200() throws Exception {
        when(workflowVersionService.getAllVersions(LegalFormCode.SARL))
                .thenReturn(List.of(buildSummary()));

        mockMvc.perform(get("/admin/workflows").param("formeJuridique", "SARL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].formeJuridique").value("SARL"));
    }

    // -----------------------------------------------------------------------
    // POST /admin/workflows → 201
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("POST /admin/workflows → 201 avec la nouvelle version")
    @WithMockUser(roles = "ADMINISTRATEUR")
    void createVersion_shouldReturn201() throws Exception {
        WorkflowVersionDetailDto detail = buildDetail();
        when(workflowVersionService.createVersion(any(CreateWorkflowVersionRequest.class)))
                .thenReturn(detail);

        CreateWorkflowVersionRequest req = new CreateWorkflowVersionRequest();
        req.setFormeJuridique(LegalFormCode.SARL);

        mockMvc.perform(post("/admin/workflows")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.numeroVersion").value("2.0"));
    }

    @Test
    @DisplayName("POST /admin/workflows sans formeJuridique → 422 VALIDATION_ERROR")
    @WithMockUser(roles = "ADMINISTRATEUR")
    void createVersion_missingFormeJuridique_shouldReturn422() throws Exception {
        // Corps vide = formeJuridique null → violation @NotNull
        mockMvc.perform(post("/admin/workflows")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // -----------------------------------------------------------------------
    // GET /admin/workflows/{id}
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("GET /admin/workflows/{id} → 200 avec détail")
    @WithMockUser(roles = "ADMINISTRATEUR")
    void getVersionById_shouldReturn200() throws Exception {
        WorkflowVersionDetailDto detail = buildDetail();
        when(workflowVersionService.getVersionById(detail.getId())).thenReturn(detail);

        mockMvc.perform(get("/admin/workflows/" + detail.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"));
    }

    @Test
    @DisplayName("GET /admin/workflows/{id} inconnu → 404")
    @WithMockUser(roles = "ADMINISTRATEUR")
    void getVersionById_unknown_shouldReturn404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(workflowVersionService.getVersionById(unknownId))
                .thenThrow(new WorkflowVersionNotFoundException(unknownId));

        mockMvc.perform(get("/admin/workflows/" + unknownId))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // POST /admin/workflows/{id}/publish
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("POST /admin/workflows/{id}/publish → 200")
    @WithMockUser(roles = "ADMINISTRATEUR")
    void publishVersion_shouldReturn200() throws Exception {
        WorkflowVersionDetailDto detail = buildDetail();
        detail.setStatut("ACTIVE");
        UUID id = UUID.randomUUID();
        when(workflowVersionService.publishVersion(id)).thenReturn(detail);

        mockMvc.perform(post("/admin/workflows/" + id + "/publish")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /admin/workflows/{id}/publish sur version ACTIVE → 409 CONFLICT")
    @WithMockUser(roles = "ADMINISTRATEUR")
    void publishVersion_activeVersion_shouldReturn409() throws Exception {
        UUID id = UUID.randomUUID();
        when(workflowVersionService.publishVersion(id))
                .thenThrow(new WorkflowVersionNotEditableException(id));

        mockMvc.perform(post("/admin/workflows/" + id + "/publish")
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    // -----------------------------------------------------------------------
    // POST /admin/workflows/{id}/archive
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("POST /admin/workflows/{id}/archive → 200")
    @WithMockUser(roles = "ADMINISTRATEUR")
    void archiverVersion_shouldReturn200() throws Exception {
        WorkflowVersionDetailDto detail = buildDetail();
        detail.setStatut("ARCHIVEE");
        UUID id = UUID.randomUUID();
        when(workflowVersionService.archiverVersion(id)).thenReturn(detail);

        mockMvc.perform(post("/admin/workflows/" + id + "/archive")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // Accès non autorisé → 403 sans rôle ADMINISTRATEUR
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("GET /admin/workflows sans rôle ADMINISTRATEUR → 403")
    @WithMockUser(roles = "ENTREPRENEUR")
    void getAllVersions_withoutAdminRole_shouldReturn403() throws Exception {
        mockMvc.perform(get("/admin/workflows"))
                .andExpect(status().isForbidden());
    }
}

