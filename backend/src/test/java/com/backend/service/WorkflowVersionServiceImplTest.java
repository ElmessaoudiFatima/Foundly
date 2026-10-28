package com.backend.service;

import com.backend.dto.workflow.*;
import com.backend.entity.EtapeWorkflow;
import com.backend.entity.WorkflowVersion;
import com.backend.enums.LegalFormCode;
import com.backend.enums.WorkflowStatus;
import com.backend.exception.WorkflowVersionNotEditableException;
import com.backend.exception.WorkflowVersionNotFoundException;
import com.backend.repository.EtapeWorkflowRepository;
import com.backend.repository.WorkflowVersionRepository;
import com.backend.service.impl.WorkflowVersionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du service WorkflowVersionServiceImpl.
 * Couvre : création, publication avec archivage, tentative de modification d'une version non-BROUILLON,
 * et réordonnancement des étapes.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowVersionServiceImplTest {

    @Mock
    private WorkflowVersionRepository workflowVersionRepository;

    @Mock
    private EtapeWorkflowRepository etapeWorkflowRepository;

    @InjectMocks
    private WorkflowVersionServiceImpl service;

    private UUID versionId;
    private WorkflowVersion brouillonVersion;
    private WorkflowVersion activeVersion;

    @BeforeEach
    void setUp() {
        versionId = UUID.randomUUID();

        brouillonVersion = new WorkflowVersion();
        brouillonVersion.setId(versionId);
        brouillonVersion.setFormeJuridique(LegalFormCode.SARL);
        brouillonVersion.setNumeroVersion("2.0");
        brouillonVersion.setStatut(WorkflowStatus.BROUILLON);

        activeVersion = new WorkflowVersion();
        activeVersion.setId(UUID.randomUUID());
        activeVersion.setFormeJuridique(LegalFormCode.SARL);
        activeVersion.setNumeroVersion("1.0");
        activeVersion.setStatut(WorkflowStatus.ACTIVE);
    }

    // -----------------------------------------------------------------------
    // 1. Création de version vierge
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("createVersion (vierge) → statut BROUILLON, numéroVersion calculé")
    void createVersion_vierge_shouldReturnBrouillon() {
        // Aucune version existante pour SARL
        when(workflowVersionRepository.findByFormeJuridiqueOrderByDateModificationDesc(LegalFormCode.SARL))
                .thenReturn(Collections.emptyList());

        WorkflowVersion saved = new WorkflowVersion();
        saved.setId(UUID.randomUUID());
        saved.setFormeJuridique(LegalFormCode.SARL);
        saved.setNumeroVersion("1.0");
        saved.setStatut(WorkflowStatus.BROUILLON);

        when(workflowVersionRepository.save(any(WorkflowVersion.class))).thenReturn(saved);
        when(workflowVersionRepository.findById(saved.getId())).thenReturn(Optional.of(saved));
        when(etapeWorkflowRepository.findByWorkflowVersionIdOrderByOrdreAsc(saved.getId()))
                .thenReturn(Collections.emptyList());

        CreateWorkflowVersionRequest request = new CreateWorkflowVersionRequest();
        request.setFormeJuridique(LegalFormCode.SARL);

        WorkflowVersionDetailDto result = service.createVersion(request);

        assertThat(result).isNotNull();
        assertThat(result.getStatut()).isEqualTo("BROUILLON");
        assertThat(result.getNumeroVersion()).isEqualTo("1.0");
        assertThat(result.getFormeJuridique()).isEqualTo("SARL");
        verify(workflowVersionRepository, times(1)).save(any(WorkflowVersion.class));
    }

    @Test
    @DisplayName("createVersion (avec versions existantes) → incrémenter numéro de version")
    void createVersion_withExistingVersions_shouldIncrementMajor() {
        when(workflowVersionRepository.findByFormeJuridiqueOrderByDateModificationDesc(LegalFormCode.SARL))
                .thenReturn(List.of(activeVersion)); // version 1.0 existe

        WorkflowVersion saved = new WorkflowVersion();
        saved.setId(UUID.randomUUID());
        saved.setFormeJuridique(LegalFormCode.SARL);
        saved.setNumeroVersion("2.0");
        saved.setStatut(WorkflowStatus.BROUILLON);

        when(workflowVersionRepository.save(any(WorkflowVersion.class))).thenReturn(saved);
        when(workflowVersionRepository.findById(saved.getId())).thenReturn(Optional.of(saved));
        when(etapeWorkflowRepository.findByWorkflowVersionIdOrderByOrdreAsc(saved.getId()))
                .thenReturn(Collections.emptyList());

        CreateWorkflowVersionRequest request = new CreateWorkflowVersionRequest();
        request.setFormeJuridique(LegalFormCode.SARL);

        WorkflowVersionDetailDto result = service.createVersion(request);

        assertThat(result.getNumeroVersion()).isEqualTo("2.0");
    }

    // -----------------------------------------------------------------------
    // 2. Publication avec archivage automatique
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("publishVersion → BROUILLON passe ACTIVE, ancienne ACTIVE archivée automatiquement")
    void publishVersion_shouldArchiveOldActive() {
        // Une étape obligatoire existe sur la version BROUILLON
        EtapeWorkflow etape = new EtapeWorkflow();
        etape.setId(UUID.randomUUID());
        etape.setObligatoire(true);
        etape.setOrdre(1);
        etape.setCode("TEST");
        etape.setLibelle("Test étape");

        when(workflowVersionRepository.findById(versionId)).thenReturn(Optional.of(brouillonVersion));
        when(etapeWorkflowRepository.findByWorkflowVersionIdOrderByOrdreAsc(versionId))
                .thenReturn(List.of(etape));
        when(workflowVersionRepository.findByFormeJuridiqueAndStatut(LegalFormCode.SARL, WorkflowStatus.ACTIVE))
                .thenReturn(Optional.of(activeVersion));
        when(workflowVersionRepository.save(any(WorkflowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workflowVersionRepository.findById(versionId)).thenReturn(Optional.of(brouillonVersion));

        // Reconfigurer le findById pour renvoyer la version après publication
        WorkflowVersion publishedVersion = new WorkflowVersion();
        publishedVersion.setId(versionId);
        publishedVersion.setFormeJuridique(LegalFormCode.SARL);
        publishedVersion.setNumeroVersion("2.0");
        publishedVersion.setStatut(WorkflowStatus.ACTIVE);

        when(workflowVersionRepository.findById(versionId))
                .thenReturn(Optional.of(brouillonVersion))
                .thenReturn(Optional.of(publishedVersion));

        service.publishVersion(versionId);

        // Vérifier que l'ancienne version ACTIVE a été passée en ARCHIVEE et sauvegardée
        verify(workflowVersionRepository, atLeast(2)).save(any(WorkflowVersion.class));
        assertThat(activeVersion.getStatut()).isEqualTo(WorkflowStatus.ARCHIVEE);
    }

    @Test
    @DisplayName("publishVersion sans étape obligatoire → IllegalStateException")
    void publishVersion_withoutObligatoryStep_shouldThrow() {
        EtapeWorkflow etapeConditionnelle = new EtapeWorkflow();
        etapeConditionnelle.setObligatoire(false);

        when(workflowVersionRepository.findById(versionId)).thenReturn(Optional.of(brouillonVersion));
        when(etapeWorkflowRepository.findByWorkflowVersionIdOrderByOrdreAsc(versionId))
                .thenReturn(List.of(etapeConditionnelle));

        assertThatThrownBy(() -> service.publishVersion(versionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("etape obligatoire");
    }

    // -----------------------------------------------------------------------
    // 3. Modification d'une version non-BROUILLON → doit échouer
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("addEtape sur version ACTIVE → WorkflowVersionNotEditableException")
    void addEtape_onActiveVersion_shouldThrow() {
        when(workflowVersionRepository.findById(activeVersion.getId()))
                .thenReturn(Optional.of(activeVersion));

        AddEtapeRequest req = new AddEtapeRequest();
        req.setCode("NOUVELLE_ETAPE");
        req.setLibelle("Nouvelle étape test");

        assertThatThrownBy(() -> service.addEtape(activeVersion.getId(), req))
                .isInstanceOf(WorkflowVersionNotEditableException.class);
    }

    @Test
    @DisplayName("updateEtape sur version ARCHIVEE → WorkflowVersionNotEditableException")
    void updateEtape_onArchivedVersion_shouldThrow() {
        WorkflowVersion archived = new WorkflowVersion();
        archived.setId(UUID.randomUUID());
        archived.setStatut(WorkflowStatus.ARCHIVEE);
        archived.setFormeJuridique(LegalFormCode.SARL);

        when(workflowVersionRepository.findById(archived.getId())).thenReturn(Optional.of(archived));

        UpdateEtapeRequest req = new UpdateEtapeRequest();
        req.setLibelle("Modifié");
        req.setObligatoire(true);
        req.setExterne(false);

        assertThatThrownBy(() -> service.updateEtape(archived.getId(), UUID.randomUUID(), req))
                .isInstanceOf(WorkflowVersionNotEditableException.class);
    }

    // -----------------------------------------------------------------------
    // 4. Réordonnancement des étapes
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("reorderEtapes → les ordres sont correctement mis à jour")
    void reorderEtapes_shouldUpdateOrdre() {
        UUID etape1Id = UUID.randomUUID();
        UUID etape2Id = UUID.randomUUID();
        UUID etape3Id = UUID.randomUUID();

        EtapeWorkflow e1 = new EtapeWorkflow();
        e1.setId(etape1Id); e1.setOrdre(1); e1.setCode("E1"); e1.setLibelle("Étape 1");
        e1.setWorkflowVersion(brouillonVersion);

        EtapeWorkflow e2 = new EtapeWorkflow();
        e2.setId(etape2Id); e2.setOrdre(2); e2.setCode("E2"); e2.setLibelle("Étape 2");
        e2.setWorkflowVersion(brouillonVersion);

        EtapeWorkflow e3 = new EtapeWorkflow();
        e3.setId(etape3Id); e3.setOrdre(3); e3.setCode("E3"); e3.setLibelle("Étape 3");
        e3.setWorkflowVersion(brouillonVersion);

        when(workflowVersionRepository.findById(versionId)).thenReturn(Optional.of(brouillonVersion));
        // Nouvel ordre : e3, e1, e2
        when(etapeWorkflowRepository.findById(etape3Id)).thenReturn(Optional.of(e3));
        when(etapeWorkflowRepository.findById(etape1Id)).thenReturn(Optional.of(e1));
        when(etapeWorkflowRepository.findById(etape2Id)).thenReturn(Optional.of(e2));
        when(etapeWorkflowRepository.save(any(EtapeWorkflow.class))).thenAnswer(inv -> inv.getArgument(0));
        when(etapeWorkflowRepository.findByWorkflowVersionIdOrderByOrdreAsc(versionId))
                .thenReturn(List.of(e3, e1, e2));

        ReorderEtapesRequest req = new ReorderEtapesRequest();
        req.setEtapeIds(List.of(etape3Id, etape1Id, etape2Id));

        service.reorderEtapes(versionId, req);

        // Vérifier que l'ordre 1 est assigné à e3, 2 à e1, 3 à e2
        assertThat(e3.getOrdre()).isEqualTo(1);
        assertThat(e1.getOrdre()).isEqualTo(2);
        assertThat(e2.getOrdre()).isEqualTo(3);
    }

    // -----------------------------------------------------------------------
    // 5. Cas de version introuvable
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("getVersionById avec ID inconnu → WorkflowVersionNotFoundException")
    void getVersionById_unknown_shouldThrow() {
        UUID unknownId = UUID.randomUUID();
        when(workflowVersionRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVersionById(unknownId))
                .isInstanceOf(WorkflowVersionNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // 6. Labels
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("labelForForme renvoie les bons libellés")
    void labelForForme_shouldReturnCorrectLabels() {
        assertThat(WorkflowVersionServiceImpl.labelForForme(LegalFormCode.AUTO_ENTREPRENEUR))
                .isEqualTo("Auto-entrepreneur");
        assertThat(WorkflowVersionServiceImpl.labelForForme(LegalFormCode.SARL)).isEqualTo("SARL");
        assertThat(WorkflowVersionServiceImpl.labelForForme(LegalFormCode.SARL_AU)).isEqualTo("SARL-AU");
        assertThat(WorkflowVersionServiceImpl.labelForForme(LegalFormCode.SA)).isEqualTo("SA");
    }

    @Test
    @DisplayName("labelForStatut renvoie les bons libellés")
    void labelForStatut_shouldReturnCorrectLabels() {
        assertThat(WorkflowVersionServiceImpl.labelForStatut(WorkflowStatus.BROUILLON)).isEqualTo("Brouillon");
        assertThat(WorkflowVersionServiceImpl.labelForStatut(WorkflowStatus.ACTIVE)).isEqualTo("Active");
        assertThat(WorkflowVersionServiceImpl.labelForStatut(WorkflowStatus.ARCHIVEE)).isEqualTo("Archivée");
    }
}

