package com.backend.service;

import com.backend.dto.agent.AgentValidateRequest;
import com.backend.dto.dossier.ChecklistItemDto;
import com.backend.dto.dossier.DossierCreateRequest;
import com.backend.dto.dossier.DossierDetailDto;
import com.backend.dto.dossier.DossierUpdateRequest;
import com.backend.dto.dossier.SubmissionResultDto;
import com.backend.entity.AuditEvent;
import com.backend.entity.DocumentEntity;
import com.backend.entity.Dossier;
import com.backend.entity.SuiviEtape;
import com.backend.entity.Utilisateur;
import com.backend.enums.ActorType;
import com.backend.enums.AuditEventAction;
import com.backend.enums.DossierStatus;
import com.backend.enums.DocumentReviewStatus;
import com.backend.enums.LegalFormCode;
import com.backend.enums.NotificationChannel;
import com.backend.enums.NotificationType;
import com.backend.enums.RoleUtilisateur;
import com.backend.enums.StepStatus;
import com.backend.exception.IncompleteChecklistException;
import com.backend.exception.InvalidDossierStateException;
import com.backend.exception.InvalidFileException;
import org.springframework.mock.web.MockMultipartFile;
import com.backend.repository.AuditEventRepository;
import com.backend.repository.DocumentRepository;
import com.backend.repository.DossierRepository;
import com.backend.repository.PaiementRepository;
import com.backend.repository.SuiviEtapeRepository;
import com.backend.service.impl.AgentServiceImpl;
import com.backend.service.impl.DocumentServiceImpl;
import com.backend.service.impl.DossierServiceImpl;
import com.backend.service.impl.WorkflowEngineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoEntrepreneurWorkflowTest {

    @Mock private DossierRepository dossierRepository;
    @Mock private SuiviEtapeRepository suiviEtapeRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private PaiementRepository paiementRepository;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private NotificationService notificationService;
    @Mock private DocumentService documentService;

    private WorkflowEngineServiceImpl workflowEngineService;
    private DossierServiceImpl dossierService;
    private AgentServiceImpl agentService;
    private DocumentServiceImpl documentServiceImpl;

    private Utilisateur entrepreneur;
    private Utilisateur agentCri;
    private Utilisateur agentDgi;

    @BeforeEach
    void setUp() {
        workflowEngineService = new WorkflowEngineServiceImpl(
                suiviEtapeRepository,
                dossierRepository,
                auditEventRepository,
                documentRepository,
                notificationService
        );

        dossierService = new DossierServiceImpl(
                dossierRepository,
                suiviEtapeRepository,
                documentRepository,
                paiementRepository,
                auditEventRepository,
                workflowEngineService,
                notificationService,
                documentService
        );

        agentService = new AgentServiceImpl(
                dossierRepository,
                suiviEtapeRepository,
                documentRepository,
                auditEventRepository,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                workflowEngineService
        );

        documentServiceImpl = new DocumentServiceImpl(
                documentRepository,
                dossierRepository,
                auditEventRepository,
                mock(PdfGenerationService.class),
                workflowEngineService
        );

        entrepreneur = new Utilisateur();
        entrepreneur.setId(UUID.randomUUID());
        entrepreneur.setNom("Benali");
        entrepreneur.setPrenom("Karim");
        entrepreneur.setEmail("karim.benali@test.ma");
        entrepreneur.setCin("BE123456");

        agentCri = new Utilisateur();
        agentCri.setId(UUID.randomUUID());
        agentCri.setNom("Berrada");
        agentCri.setPrenom("Khadija");
        agentCri.setEmail("khadija.berrada@cri.ma");
        agentCri.setOrganisme("CRI");

        agentDgi = new Utilisateur();
        agentDgi.setId(UUID.randomUUID());
        agentDgi.setNom("Amrani");
        agentDgi.setPrenom("Laila");
        agentDgi.setEmail("l.amrani@dgi.gov.ma");
        agentDgi.setOrganisme("DGI");
    }

    // =========================================================================
    // 1. CRÉATION ET GESTION DU CAPITAL AUTO-ENTREPRENEUR (RG-AE-01)
    // =========================================================================

    @Test
    @DisplayName("RG-AE-01 : La création d'un dossier Auto-Entrepreneur force le capital à 0 MAD en BDD")
    void shouldForceCapitalToZeroOnCreateAutoEntrepreneur() {
        DossierCreateRequest request = new DossierCreateRequest(
                LegalFormCode.AUTO_ENTREPRENEUR,
                "Consulting Pro",
                "Casablanca-Settat",
                "Conseil en informatique",
                new BigDecimal("50000.00"), // Tentative de définir un capital
                "12 Boulevard d'Anfa"
        );

        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> {
            Dossier d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(suiviEtapeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(any())).thenReturn(Collections.emptyList());
        when(documentService.getChecklist(any(), any())).thenReturn(Collections.emptyList());

        DossierDetailDto dto = dossierService.createDossier(request, entrepreneur);

        assertNotNull(dto);
        assertEquals(BigDecimal.ZERO, dto.capitalAmountMad(), "Le capital AE doit être strictement 0 MAD");
        assertFalse(dto.capitalBlockageRequired(), "Le blocage bancaire ne doit jamais être requis pour AE");

        ArgumentCaptor<Dossier> captor = ArgumentCaptor.forClass(Dossier.class);
        verify(dossierRepository, atLeastOnce()).save(captor.capture());
        Dossier savedDossier = captor.getValue();
        assertEquals(BigDecimal.ZERO, savedDossier.getCapitalMad());
        assertFalse(savedDossier.isCapitalBlockageRequired());
    }

    @Test
    @DisplayName("RG-AE-01 : La mise à jour d'un dossier Auto-Entrepreneur ignore toute tentative de capital > 0")
    void shouldMaintainZeroCapitalOnUpdateAutoEntrepreneur() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.AUTO_ENTREPRENEUR);
        dossier.setStatut(DossierStatus.BROUILLON);
        dossier.setCapitalMad(BigDecimal.ZERO);
        dossier.setEntrepreneur(entrepreneur);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId)).thenReturn(Collections.emptyList());
        when(documentService.getChecklist(eq(dossierId), any())).thenReturn(Collections.emptyList());

        DossierUpdateRequest updateReq = new DossierUpdateRequest(
                LegalFormCode.AUTO_ENTREPRENEUR,
                "Consulting Pro Updated",
                "Rabat-Salé-Kénitra",
                "Nouvelle description",
                new BigDecimal("80000.00"), // Tentative d'injection de capital
                "Nouvelle adresse"
        );

        DossierDetailDto updated = dossierService.updateDossier(dossierId, updateReq, entrepreneur);

        assertNotNull(updated);
        assertEquals(BigDecimal.ZERO, updated.capitalAmountMad(), "Le capital AE doit demeurer à 0 MAD");
        assertFalse(updated.capitalBlockageRequired());
    }

    // =========================================================================
    // 2. WORKFLOW INITIALISATION & CHECKLIST (4 ÉTAPES)
    // =========================================================================

    @Test
    @DisplayName("Workflow AE : Initialisation stricte en 4 étapes")
    void shouldInitializeExactlyFourStepsForAutoEntrepreneur() {
        Dossier dossier = new Dossier();
        dossier.setId(UUID.randomUUID());
        dossier.setFormeJuridique(LegalFormCode.AUTO_ENTREPRENEUR);
        dossier.setNomEntreprise("AE Test");

        when(suiviEtapeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<SuiviEtape> steps = workflowEngineService.initializeStepsForDossier(dossier);

        assertEquals(4, steps.size(), "Le parcours Auto-Entrepreneur doit comporter exactement 4 étapes");

        SuiviEtape step1 = steps.get(0);
        assertEquals("VERIFICATION_ELIGIBILITE", step1.getEtapeCode());
        assertEquals("CRI", step1.getOrganismeCode());
        assertEquals(ActorType.AGENT, step1.getActionOwner());
        assertEquals(StepStatus.EN_COURS, step1.getStatut());

        SuiviEtape step2 = steps.get(1);
        assertEquals("FORMULAIRE_INSCRIPTION", step2.getEtapeCode());
        assertNull(step2.getOrganismeCode());
        assertEquals(ActorType.ENTREPRENEUR, step2.getActionOwner());
        assertEquals(StepStatus.EN_ATTENTE, step2.getStatut());

        SuiviEtape step3 = steps.get(2);
        assertEquals("VALIDATION_POSTE_MAROC", step3.getEtapeCode());
        assertEquals("CRI", step3.getOrganismeCode());
        assertEquals(ActorType.AGENT, step3.getActionOwner());
        assertEquals(StepStatus.EN_ATTENTE, step3.getStatut());

        SuiviEtape step4 = steps.get(3);
        assertEquals("DELIVRANCE_CARTE", step4.getEtapeCode());
        assertEquals("DGI", step4.getOrganismeCode());
        assertEquals(ActorType.AGENT, step4.getActionOwner());
        assertEquals(StepStatus.EN_ATTENTE, step4.getStatut());
    }

    // =========================================================================
    // 3. SOUMISSION ET CONTRÔLE DE COMPLÉTUDE
    // =========================================================================

    @Test
    @DisplayName("RG6 : Rejet de soumission si documents obligatoires manquants pour AE")
    void shouldRejectSubmissionIfIncompleteForAE() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.AUTO_ENTREPRENEUR);
        dossier.setStatut(DossierStatus.BROUILLON);
        dossier.setEntrepreneur(entrepreneur);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        // Checklist où FORMULAIRE_UNIQUE manque
        List<ChecklistItemDto> checklist = List.of(
                new ChecklistItemDto("CIN", "Carte Nationale d'Identité", true, List.of("application/pdf"), true, UUID.randomUUID(), "cin.pdf", false, null),
                new ChecklistItemDto("JUSTIFICATIF_SIEGE", "Justificatif de siège", true, List.of("application/pdf"), true, UUID.randomUUID(), "bail.pdf", false, null),
                new ChecklistItemDto("FORMULAIRE_UNIQUE", "Formulaire d'inscription RNAE", true, List.of("application/pdf"), false, null, null, false, null)
        );
        when(documentService.getChecklist(dossierId, entrepreneur)).thenReturn(checklist);

        IncompleteChecklistException ex = assertThrows(IncompleteChecklistException.class, () ->
                dossierService.submitDossier(dossierId, entrepreneur)
        );

        assertTrue(ex.getMissingDocuments().contains("Formulaire d'inscription RNAE"));
        verify(dossierRepository, never()).save(any());
    }

    // =========================================================================
    // 4. TRANSITION WORKFLOW & AUTO-VALIDATION ÉTAPE 2 (RG-AE-02)
    // =========================================================================

    @Test
    @DisplayName("RG-AE-02 : Validation étape 1 CRI auto-complète FORMULAIRE_INSCRIPTION et passe à étape 3 CRI")
    void shouldAutoCompleteFormulaireInscriptionAndAdvanceToStep3() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.AUTO_ENTREPRENEUR);
        dossier.setStatut(DossierStatus.EN_COURS);
        dossier.setNomEntreprise("AE Auto");
        dossier.setEntrepreneur(entrepreneur);

        SuiviEtape step1 = new SuiviEtape();
        step1.setId(UUID.randomUUID());
        step1.setDossier(dossier);
        step1.setOrdre(1);
        step1.setEtapeCode("VERIFICATION_ELIGIBILITE");
        step1.setLibelle("Vérification de l'éligibilité");
        step1.setStatut(StepStatus.EN_COURS);
        step1.setActionOwner(ActorType.AGENT);
        step1.setOrganismeCode("CRI");
        step1.setStartedAt(Instant.now());

        SuiviEtape step2 = new SuiviEtape();
        step2.setId(UUID.randomUUID());
        step2.setDossier(dossier);
        step2.setOrdre(2);
        step2.setEtapeCode("FORMULAIRE_INSCRIPTION");
        step2.setLibelle("Dépôt du formulaire RNAE et justificatifs");
        step2.setStatut(StepStatus.EN_ATTENTE);
        step2.setActionOwner(ActorType.ENTREPRENEUR);

        SuiviEtape step3 = new SuiviEtape();
        step3.setId(UUID.randomUUID());
        step3.setDossier(dossier);
        step3.setOrdre(3);
        step3.setEtapeCode("VALIDATION_POSTE_MAROC");
        step3.setLibelle("Validation d'identité en agence et conformité");
        step3.setStatut(StepStatus.EN_ATTENTE);
        step3.setActionOwner(ActorType.AGENT);
        step3.setOrganismeCode("CRI");

        SuiviEtape step4 = new SuiviEtape();
        step4.setId(UUID.randomUUID());
        step4.setDossier(dossier);
        step4.setOrdre(4);
        step4.setEtapeCode("DELIVRANCE_CARTE");
        step4.setLibelle("Attribution identifiant fiscal et délivrance de la carte");
        step4.setStatut(StepStatus.EN_ATTENTE);
        step4.setActionOwner(ActorType.AGENT);
        step4.setOrganismeCode("DGI");

        List<SuiviEtape> steps = List.of(step1, step2, step3, step4);
        when(suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId)).thenReturn(steps);
        when(suiviEtapeRepository.save(any(SuiviEtape.class))).thenAnswer(inv -> inv.getArgument(0));

        // Déclenchement de l'avancement suite à validation de l'étape 1 par CRI
        workflowEngineService.advanceWorkflow(dossier, "Éligibilité confirmée", agentCri);

        // 1. Étape 1 doit être VALIDÉE par agentCri
        assertEquals(StepStatus.VALIDEE, step1.getStatut());
        assertNotNull(step1.getDecidedAt());

        // 2. Étape 2 doit être AUTO-VALIDÉE par SYSTEME
        assertEquals(StepStatus.VALIDEE, step2.getStatut());
        assertNotNull(step2.getDecidedAt());

        // 3. Étape 3 doit être active (EN_COURS)
        assertEquals(StepStatus.EN_COURS, step3.getStatut());
        assertNotNull(step3.getStartedAt());

        // 4. Étape 4 doit être EN_ATTENTE
        assertEquals(StepStatus.EN_ATTENTE, step4.getStatut());

        // 5. Dossier pointe sur l'étape 3
        assertEquals("VALIDATION_POSTE_MAROC", dossier.getCurrentStepCode());
        assertEquals(3, dossier.getCurrentStepOrder());

        // Vérification des AuditEvents enregistrés
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository, atLeast(2)).save(auditCaptor.capture());
        List<AuditEvent> auditEvents = auditCaptor.getAllValues();

        boolean foundStep2SystemAudit = auditEvents.stream().anyMatch(e ->
                e.getStepTrackingId().equals(step2.getId())
                && e.getActorType() == ActorType.SYSTEME
                && e.getDetails().contains("Prise en compte technique")
        );
        assertTrue(foundStep2SystemAudit, "Un AuditEvent SYSTEME doit tracer la prise en compte technique du formulaire RNAE distincte de la validation administrative");
    }

    // =========================================================================
    // 5. VALIDATION AGENT CRI (PAS DE STATUTS_SIGNES REQUIS POUR AE)
    // =========================================================================

    @Test
    @DisplayName("Agent CRI : Validation d'un dossier AE ne réclame PAS de STATUTS_SIGNES")
    void shouldValidateAutoEntrepreneurWithoutStatutsSignes() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.AUTO_ENTREPRENEUR);
        dossier.setStatut(DossierStatus.EN_COURS);
        dossier.setCapitalBlockageRequired(false);

        SuiviEtape step1 = new SuiviEtape();
        step1.setId(UUID.randomUUID());
        step1.setDossier(dossier);
        step1.setEtapeCode("VERIFICATION_ELIGIBILITE");
        step1.setOrganismeCode("CRI");
        step1.setStatut(StepStatus.EN_COURS);
        step1.setActionOwner(ActorType.AGENT);
        step1.setClaimedByAgent(agentCri);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(suiviEtapeRepository.findFirstByDossierIdAndStatutOrderByOrdreAsc(dossierId, StepStatus.EN_COURS)).thenReturn(Optional.of(step1));
        when(suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId)).thenReturn(List.of(step1));

        // Documents déposés pour AE : CIN, JUSTIFICATIF_SIEGE et FORMULAIRE_UNIQUE (AUCUN STATUTS_SIGNES)
        DocumentEntity docCin = new DocumentEntity();
        docCin.setDocumentTypeCode("CIN");
        docCin.setReviewStatus(DocumentReviewStatus.CONFORME);

        DocumentEntity docSiege = new DocumentEntity();
        docSiege.setDocumentTypeCode("JUSTIFICATIF_SIEGE");
        docSiege.setReviewStatus(DocumentReviewStatus.CONFORME);

        DocumentEntity docRnae = new DocumentEntity();
        docRnae.setDocumentTypeCode("FORMULAIRE_UNIQUE");
        docRnae.setReviewStatus(DocumentReviewStatus.CONFORME);

        when(documentRepository.findByDossierIdOrderByUploadedAtDesc(dossierId)).thenReturn(List.of(docCin, docSiege, docRnae));

        AgentValidateRequest request = new AgentValidateRequest(
                "Dossier éligible et conforme",
                Map.of(
                        "companyFileComplete", true,
                        "companyDetailsConsistent", true,
                        "documentsComplete", true
                ),
                Map.of("depositReceiptNumber", "REC-CRI-2026-001")
        );

        // Ne doit PAS lever d'exception InvalidDossierStateException
        assertDoesNotThrow(() -> agentService.validateStep(dossierId, request, agentCri));

        // Les résultats doivent être stockés en JSON
        assertNotNull(step1.getValidationResultJson());
        assertTrue(step1.getValidationResultJson().contains("REC-CRI-2026-001"));
    }

    // =========================================================================
    // 6. NON-RÉGRESSION SARL & SA
    // =========================================================================

    @Test
    @DisplayName("Non-régression SARL : L'étape REDACTION_STATUTS n'est PAS auto-validée")
    void shouldNotAutoValidateRedactionStatutsForSARL() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.SARL);
        dossier.setStatut(DossierStatus.EN_COURS);
        dossier.setNomEntreprise("SARL Pro");
        dossier.setEntrepreneur(entrepreneur);

        SuiviEtape step1 = new SuiviEtape();
        step1.setId(UUID.randomUUID());
        step1.setDossier(dossier);
        step1.setOrdre(1);
        step1.setEtapeCode("CERTIFICAT_NEGATIF");
        step1.setStatut(StepStatus.EN_COURS);
        step1.setActionOwner(ActorType.AGENT);
        step1.setOrganismeCode("OMPIC");

        SuiviEtape step2 = new SuiviEtape();
        step2.setId(UUID.randomUUID());
        step2.setDossier(dossier);
        step2.setOrdre(2);
        step2.setEtapeCode("REDACTION_STATUTS");
        step2.setStatut(StepStatus.EN_ATTENTE);
        step2.setActionOwner(ActorType.ENTREPRENEUR);
        step2.setOrganismeCode("CRI");

        List<SuiviEtape> steps = List.of(step1, step2);
        when(suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId)).thenReturn(steps);
        when(suiviEtapeRepository.save(any(SuiviEtape.class))).thenAnswer(inv -> inv.getArgument(0));

        // Avancement après étape 1 OMPIC
        workflowEngineService.advanceWorkflow(dossier, "Certificat délivré", agentCri);

        // Étape 1 validée
        assertEquals(StepStatus.VALIDEE, step1.getStatut());

        // Étape 2 REDACTION_STATUTS doit rester EN_COURS et NE PAS être auto-validée
        assertEquals(StepStatus.EN_COURS, step2.getStatut());
        assertNull(step2.getDecidedAt(), "REDACTION_STATUTS ne doit pas avoir de date de décision");
        assertEquals("REDACTION_STATUTS", dossier.getCurrentStepCode());
    }

    @Test
    @DisplayName("Non-régression CRI SARL : Exige STATUTS_SIGNES lors de la validation")
    void shouldRequireStatutsSignesForSarlUnderCri() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.SARL);
        dossier.setStatut(DossierStatus.EN_COURS);
        dossier.setCapitalBlockageRequired(false);

        SuiviEtape stepCri = new SuiviEtape();
        stepCri.setId(UUID.randomUUID());
        stepCri.setDossier(dossier);
        stepCri.setEtapeCode("DEPOT_CRI");
        stepCri.setOrganismeCode("CRI");
        stepCri.setStatut(StepStatus.EN_COURS);
        stepCri.setActionOwner(ActorType.AGENT);
        stepCri.setClaimedByAgent(agentCri);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(suiviEtapeRepository.findFirstByDossierIdAndStatutOrderByOrdreAsc(dossierId, StepStatus.EN_COURS)).thenReturn(Optional.of(stepCri));

        // Documents sans STATUTS_SIGNES
        DocumentEntity docCin = new DocumentEntity();
        docCin.setDocumentTypeCode("CIN");
        docCin.setReviewStatus(DocumentReviewStatus.CONFORME);

        DocumentEntity docRnae = new DocumentEntity();
        docRnae.setDocumentTypeCode("FORMULAIRE_UNIQUE");
        docRnae.setReviewStatus(DocumentReviewStatus.CONFORME);

        when(documentRepository.findByDossierIdOrderByUploadedAtDesc(dossierId)).thenReturn(List.of(docCin, docRnae));

        AgentValidateRequest request = new AgentValidateRequest(
                "OK",
                Map.of("companyFileComplete", true, "companyDetailsConsistent", true, "documentsComplete", true),
                Map.of("depositReceiptNumber", "REC-001")
        );

        // Doit refuser la validation car STATUTS_SIGNES est obligatoire pour une SARL
        assertThrows(InvalidDossierStateException.class, () ->
                agentService.validateStep(dossierId, request, agentCri)
        );
    }

    // =========================================================================
    // 7. REJET, CORRECTION ET DGI ÉTAPE 4 POUR AUTO-ENTREPRENEUR
    // =========================================================================

    @Test
    @DisplayName("Rejet RG8 & Correction : L'agent CRI rejette avec motif obligatoire, puis l'entrepreneur corrige")
    void shouldHandleRejectionAndResubmissionForAutoEntrepreneur() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.AUTO_ENTREPRENEUR);
        dossier.setStatut(DossierStatus.EN_COURS);
        dossier.setEntrepreneur(entrepreneur);

        SuiviEtape step3 = new SuiviEtape();
        step3.setId(UUID.randomUUID());
        step3.setDossier(dossier);
        step3.setOrdre(3);
        step3.setEtapeCode("VALIDATION_POSTE_MAROC");
        step3.setLibelle("Validation d'identité en agence et conformité");
        step3.setStatut(StepStatus.EN_COURS);
        step3.setActionOwner(ActorType.AGENT);
        step3.setOrganismeCode("CRI");
        step3.setClaimedByAgent(agentCri);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(suiviEtapeRepository.findFirstByDossierIdAndStatutOrderByOrdreAsc(dossierId, StepStatus.EN_COURS)).thenReturn(Optional.of(step3));
        when(suiviEtapeRepository.findById(step3.getId())).thenReturn(Optional.of(step3));
        when(suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId)).thenReturn(List.of(step3));

        // 1. Rejet par l'agent CRI avec motif obligatoire (>= 10 caractères)
        com.backend.dto.agent.AgentRejectRequest rejectReq = new com.backend.dto.agent.AgentRejectRequest("Justificatif de domicile illisible, veuillez fournir une facture récente");
        agentService.rejectStep(dossierId, rejectReq, agentCri);

        assertEquals(StepStatus.REJETEE, step3.getStatut());
        assertEquals("Justificatif de domicile illisible, veuillez fournir une facture récente", step3.getMotifRejet());
        assertEquals(DossierStatus.A_CORRIGER, dossier.getStatut());
        verify(notificationService).sendNotification(eq(entrepreneur), eq(dossier), anyString(), anyString(), eq(NotificationChannel.IN_APP), eq(NotificationType.ETAPE_REJETEE));

        // 2. L'entrepreneur corrige et re-soumet son dossier
        when(documentService.getChecklist(dossierId, entrepreneur)).thenReturn(List.of(
                new ChecklistItemDto("CIN", "CIN", true, List.of("application/pdf"), true, UUID.randomUUID(), "cin.pdf", false, null),
                new ChecklistItemDto("JUSTIFICATIF_SIEGE", "Siège", true, List.of("application/pdf"), true, UUID.randomUUID(), "facture.pdf", false, null),
                new ChecklistItemDto("FORMULAIRE_UNIQUE", "RNAE", true, List.of("application/pdf"), true, UUID.randomUUID(), "form.pdf", false, null)
        ));

        SubmissionResultDto res = dossierService.submitDossier(dossierId, entrepreneur);
        assertEquals(DossierStatus.EN_COURS, res.status());
        assertEquals(StepStatus.EN_COURS, step3.getStatut());
        assertNull(dossier.getRejectionReason());
    }

    @Test
    @DisplayName("DGI Étape 4 : Validation finale par l'agent DGI et passage du dossier au statut FINALISE")
    void shouldValidateStep4DgiAndFinalizeDossierForAutoEntrepreneur() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.AUTO_ENTREPRENEUR);
        dossier.setStatut(DossierStatus.EN_COURS);
        dossier.setNomEntreprise("Tech AE");
        dossier.setEntrepreneur(entrepreneur);

        SuiviEtape step4 = new SuiviEtape();
        step4.setId(UUID.randomUUID());
        step4.setDossier(dossier);
        step4.setOrdre(4);
        step4.setEtapeCode("DELIVRANCE_CARTE");
        step4.setLibelle("Attribution identifiant fiscal et délivrance de la carte");
        step4.setStatut(StepStatus.EN_COURS);
        step4.setActionOwner(ActorType.AGENT);
        step4.setOrganismeCode("DGI");
        step4.setClaimedByAgent(agentDgi);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(suiviEtapeRepository.findFirstByDossierIdAndStatutOrderByOrdreAsc(dossierId, StepStatus.EN_COURS)).thenReturn(Optional.of(step4));
        when(suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId)).thenReturn(List.of(step4));

        DocumentEntity docCin = new DocumentEntity();
        docCin.setDocumentTypeCode("CIN");
        docCin.setReviewStatus(DocumentReviewStatus.CONFORME);

        DocumentEntity docSiege = new DocumentEntity();
        docSiege.setDocumentTypeCode("JUSTIFICATIF_SIEGE");
        docSiege.setReviewStatus(DocumentReviewStatus.CONFORME);

        DocumentEntity docRnae = new DocumentEntity();
        docRnae.setDocumentTypeCode("FORMULAIRE_UNIQUE");
        docRnae.setReviewStatus(DocumentReviewStatus.CONFORME);

        when(documentRepository.findByDossierIdOrderByUploadedAtDesc(dossierId)).thenReturn(List.of(docCin, docSiege, docRnae));

        AgentValidateRequest dgiRequest = new AgentValidateRequest(
                "Attribution carte et IF effectuée",
                Map.of(
                        "taxIdentityVerified", true,
                        "activityTaxChecked", true,
                        "supportingDocuments", true
                ),
                Map.of(
                        "taxIdentifier", "IF-99887766",
                        "professionalTaxCertificate", "RNAE-2026-12345"
                )
        );

        assertDoesNotThrow(() -> agentService.validateStep(dossierId, dgiRequest, agentDgi));

        assertEquals(StepStatus.VALIDEE, step4.getStatut());
        assertEquals(DossierStatus.FINALISE, dossier.getStatut());
        assertNotNull(dossier.getFinalizedAt());
        verify(notificationService).sendNotification(eq(entrepreneur), eq(dossier), anyString(), anyString(), eq(NotificationChannel.IN_APP), eq(NotificationType.DOSSIER_FINALISE));
    }

    // =========================================================================
    // 8. TESTS RG6 FORMATS ET TAILLES DE DOCUMENTS
    // =========================================================================

    @Test
    @DisplayName("RG6 : Rejet des formats non autorisés (.txt, .exe)")
    void shouldRejectUploadWithUnauthorizedExtension() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setStatut(DossierStatus.BROUILLON);
        dossier.setEntrepreneur(entrepreneur);
        entrepreneur.setRole(RoleUtilisateur.ENTREPRENEUR);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        MockMultipartFile txtFile = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                "Contenu non autorisé".getBytes()
        );

        InvalidFileException ex = assertThrows(InvalidFileException.class, () ->
                documentServiceImpl.uploadDocument(dossierId, txtFile, "CIN", null, entrepreneur)
        );

        assertTrue(ex.getMessage().contains("Extension de fichier non autorisée"), "Doit explicitement refuser l'extension non autorisée");
    }

    @Test
    @DisplayName("RG6 : Rejet des fichiers dépassant la limite de 5 Mo")
    void shouldRejectUploadWithOversizedFile() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setStatut(DossierStatus.BROUILLON);
        dossier.setEntrepreneur(entrepreneur);
        entrepreneur.setRole(RoleUtilisateur.ENTREPRENEUR);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        // Fichier de 6 Mo (limite = 5 Mo)
        byte[] oversizedContent = new byte[6 * 1024 * 1024];
        MockMultipartFile bigFile = new MockMultipartFile(
                "file",
                "gros_fichier.pdf",
                "application/pdf",
                oversizedContent
        );

        InvalidFileException ex = assertThrows(InvalidFileException.class, () ->
                documentServiceImpl.uploadDocument(dossierId, bigFile, "CIN", null, entrepreneur)
        );

        assertTrue(ex.getMessage().contains("5 Mo (RG6)"), "Doit refuser les fichiers supérieurs à 5 Mo");
    }

    @Test
    @DisplayName("DGI & Statut : Le dossier NE PEUT PAS être FINALISE avant la validation DGI (étape 4)")
    void shouldNotFinalizeDossierBeforeDgiStepIsCompleted() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.AUTO_ENTREPRENEUR);
        dossier.setStatut(DossierStatus.EN_COURS);
        dossier.setNomEntreprise("AE Non Finalisée");
        dossier.setEntrepreneur(entrepreneur);

        SuiviEtape step3 = new SuiviEtape();
        step3.setId(UUID.randomUUID());
        step3.setDossier(dossier);
        step3.setOrdre(3);
        step3.setEtapeCode("VALIDATION_POSTE_MAROC");
        step3.setStatut(StepStatus.EN_COURS);
        step3.setActionOwner(ActorType.AGENT);
        step3.setOrganismeCode("CRI");

        SuiviEtape step4 = new SuiviEtape();
        step4.setId(UUID.randomUUID());
        step4.setDossier(dossier);
        step4.setOrdre(4);
        step4.setEtapeCode("DELIVRANCE_CARTE");
        step4.setStatut(StepStatus.EN_ATTENTE);
        step4.setActionOwner(ActorType.AGENT);
        step4.setOrganismeCode("DGI");

        when(suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId)).thenReturn(List.of(step3, step4));
        when(suiviEtapeRepository.save(any(SuiviEtape.class))).thenAnswer(inv -> inv.getArgument(0));

        // L'agent CRI valide l'étape 3
        workflowEngineService.advanceWorkflow(dossier, "Conformité validée", agentCri);

        // L'étape 3 est VALIDEE mais l'étape 4 est désormais EN_COURS
        assertEquals(StepStatus.VALIDEE, step3.getStatut());
        assertEquals(StepStatus.EN_COURS, step4.getStatut());

        // Le dossier DOIT rester EN_COURS et NE PAS être FINALISE
        assertEquals(DossierStatus.EN_COURS, dossier.getStatut());
        assertNull(dossier.getFinalizedAt(), "Le dossier ne doit avoir aucun finalizedAt avant la DGI");
        assertEquals("DELIVRANCE_CARTE", dossier.getCurrentStepCode());
    }
}
