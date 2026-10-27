package com.backend.service;

import com.backend.dto.dossier.ChecklistItemDto;
import com.backend.dto.dossier.DossierCreateRequest;
import com.backend.dto.dossier.DossierDetailDto;
import com.backend.dto.dossier.SubmissionResultDto;
import com.backend.entity.Dossier;
import com.backend.entity.SuiviEtape;
import com.backend.entity.Utilisateur;
import com.backend.enums.ActorType;
import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;
import com.backend.enums.StepStatus;
import com.backend.exception.IncompleteChecklistException;
import com.backend.repository.AuditEventRepository;
import com.backend.repository.DocumentRepository;
import com.backend.repository.DossierRepository;
import com.backend.repository.SuiviEtapeRepository;
import com.backend.service.impl.DossierServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DossierServiceImplTest {

    @Mock
    private DossierRepository dossierRepository;
    @Mock
    private SuiviEtapeRepository suiviEtapeRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private WorkflowEngineService workflowEngineService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private DocumentService documentService;

    @InjectMocks
    private DossierServiceImpl dossierService;

    private Utilisateur entrepreneur;

    @BeforeEach
    void setUp() {
        entrepreneur = new Utilisateur();
        entrepreneur.setId(UUID.randomUUID());
        entrepreneur.setPrenom("Aya");
        entrepreneur.setNom("El Mansouri");
        entrepreneur.setEmail("aya.mansouri@gmail.com");
    }

    @Test
    @DisplayName("RG15 - La création d'une SARL avec capital > 100 000 MAD doit exiger le blocage du capital")
    void shouldRequireCapitalBlockageWhenCapitalExceeds100k() {
        DossierCreateRequest req = new DossierCreateRequest(
                LegalFormCode.SARL,
                "Atlas Tech SARL",
                "Casablanca-Settat",
                "Conseil IT",
                new BigDecimal("150000"),
                "123 Bd Zerktouni"
        );

        when(dossierRepository.save(any(Dossier.class))).thenAnswer(invocation -> {
            Dossier d = invocation.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(workflowEngineService.initializeStepsForDossier(any(Dossier.class))).thenReturn(new ArrayList<>());
        when(suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(any(UUID.class))).thenReturn(new ArrayList<>());
        when(documentService.getChecklist(any(UUID.class), any(Utilisateur.class))).thenReturn(new ArrayList<>());

        DossierDetailDto result = dossierService.createDossier(req, entrepreneur);

        assertNotNull(result);
        assertTrue(result.capitalBlockageRequired(), "Le blocage de capital doit être requis pour un capital > 100 000 MAD");
        assertEquals(DossierStatus.BROUILLON, result.status());
    }

    @Test
    @DisplayName("RG15 - La création d'une SARL avec capital <= 100 000 MAD ne doit PAS exiger le blocage du capital")
    void shouldNotRequireCapitalBlockageWhenCapitalLessThanOrEqual100k() {
        DossierCreateRequest req = new DossierCreateRequest(
                LegalFormCode.SARL,
                "Small Business SARL",
                "Casablanca-Settat",
                "Commerce",
                new BigDecimal("50000"),
                "45 Rue Ibn Batouta"
        );

        when(dossierRepository.save(any(Dossier.class))).thenAnswer(invocation -> {
            Dossier d = invocation.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(workflowEngineService.initializeStepsForDossier(any(Dossier.class))).thenReturn(new ArrayList<>());
        when(suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(any(UUID.class))).thenReturn(new ArrayList<>());
        when(documentService.getChecklist(any(UUID.class), any(Utilisateur.class))).thenReturn(new ArrayList<>());

        DossierDetailDto result = dossierService.createDossier(req, entrepreneur);

        assertNotNull(result);
        assertFalse(result.capitalBlockageRequired(), "Le blocage ne doit pas être requis pour capital <= 100 000 MAD");
    }

    @Test
    @DisplayName("RG6 - La soumission échoue si des pièces obligatoires sont manquantes")
    void shouldThrowExceptionWhenSubmittingIncompleteChecklist() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setStatut(DossierStatus.BROUILLON);
        dossier.setEntrepreneur(entrepreneur);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        List<ChecklistItemDto> incompleteChecklist = List.of(
                new ChecklistItemDto("CIN", "Carte Nationale d'Identité", true, List.of("application/pdf"), true, UUID.randomUUID(), "cin.pdf", false, null),
                new ChecklistItemDto("JUSTIFICATIF_SIEGE", "Justificatif de domicile", true, List.of("application/pdf"), false, null, null, false, null)
        );
        when(documentService.getChecklist(dossierId, entrepreneur)).thenReturn(incompleteChecklist);

        IncompleteChecklistException ex = assertThrows(IncompleteChecklistException.class, () ->
                dossierService.submitDossier(dossierId, entrepreneur)
        );

        assertTrue(ex.getMissingDocuments().contains("Justificatif de domicile"));
        verify(dossierRepository, never()).save(any(Dossier.class));
    }

    @Test
    @DisplayName("RG6 - La soumission réussit lorsque toutes les pièces obligatoires sont soumises")
    void shouldSucceedWhenAllRequiredDocumentsArePresent() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setReference("FND-2026-00001");
        dossier.setStatut(DossierStatus.BROUILLON);
        dossier.setCurrentStepCode("CERTIFICAT_NEGATIF");
        dossier.setCurrentStepOrder(1);
        dossier.setEtapeActuelle("Certificat négatif");
        dossier.setEntrepreneur(entrepreneur);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(i -> i.getArgument(0));

        List<ChecklistItemDto> completeChecklist = List.of(
                new ChecklistItemDto("CIN", "Carte Nationale d'Identité", true, List.of("application/pdf"), true, UUID.randomUUID(), "cin.pdf", false, null),
                new ChecklistItemDto("JUSTIFICATIF_SIEGE", "Justificatif de domicile", true, List.of("application/pdf"), true, UUID.randomUUID(), "bail.pdf", false, null)
        );
        when(documentService.getChecklist(dossierId, entrepreneur)).thenReturn(completeChecklist);

        SubmissionResultDto result = dossierService.submitDossier(dossierId, entrepreneur);

        assertNotNull(result);
        assertEquals(DossierStatus.SOUMIS, result.status());
        verify(notificationService).sendNotification(any(), any(), any(), any(), any(), any());
        verify(auditEventRepository).save(any());
    }
}
