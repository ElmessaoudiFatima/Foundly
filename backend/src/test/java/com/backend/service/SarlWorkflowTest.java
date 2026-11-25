package com.backend.service;

import com.backend.dto.agent.AgentValidateRequest;
import com.backend.dto.dossier.*;
import com.backend.entity.*;
import com.backend.enums.*;
import com.backend.exception.*;
import com.backend.repository.*;
import com.backend.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SarlWorkflowTest {

    @Mock private DossierRepository dossierRepository;
    @Mock private SuiviEtapeRepository suiviEtapeRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private PaiementRepository paiementRepository;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private NotificationService notificationService;
    @Mock private AssocieRepository associeRepository;
    @Mock private GerantRepository gerantRepository;
    @Mock private PdfGenerationService pdfGenerationService;

    private WorkflowEngineServiceImpl workflowEngineService;
    private DossierServiceImpl dossierService;
    private DocumentServiceImpl documentService;
    private AgentServiceImpl agentService;

    private Utilisateur entrepreneur;
    private Utilisateur agentCri;

    @BeforeEach
    void setUp() {
        entrepreneur = new Utilisateur();
        entrepreneur.setId(UUID.randomUUID());
        entrepreneur.setPrenom("Yassine");
        entrepreneur.setNom("El Amrani");
        entrepreneur.setEmail("yassine@foundly.ma");
        entrepreneur.setCin("AB123456");
        entrepreneur.setRole(RoleUtilisateur.ENTREPRENEUR);

        agentCri = new Utilisateur();
        agentCri.setId(UUID.randomUUID());
        agentCri.setPrenom("Karim");
        agentCri.setNom("Berrada");
        agentCri.setEmail("agent.cri@foundly.ma");
        agentCri.setRole(RoleUtilisateur.AGENT);
        agentCri.setOrganisme("CRI");

        workflowEngineService = new WorkflowEngineServiceImpl(
                suiviEtapeRepository,
                dossierRepository,
                auditEventRepository,
                documentRepository,
                notificationService
        );

        documentService = new DocumentServiceImpl(
                documentRepository,
                dossierRepository,
                auditEventRepository,
                pdfGenerationService,
                workflowEngineService,
                associeRepository,
                gerantRepository
        );

        dossierService = new DossierServiceImpl(
                dossierRepository,
                suiviEtapeRepository,
                documentRepository,
                paiementRepository,
                auditEventRepository,
                workflowEngineService,
                notificationService,
                documentService,
                associeRepository,
                gerantRepository
        );

        agentService = new AgentServiceImpl(
                dossierRepository,
                suiviEtapeRepository,
                documentRepository,
                auditEventRepository,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                workflowEngineService
        );

        Map<UUID, Dossier> dossierStore = new java.util.concurrent.ConcurrentHashMap<>();
        lenient().when(dossierRepository.save(any(Dossier.class))).thenAnswer(i -> {
            Dossier d = i.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            dossierStore.put(d.getId(), d);
            return d;
        });
        lenient().when(dossierRepository.findById(any(UUID.class))).thenAnswer(i -> {
            UUID id = i.getArgument(0);
            return Optional.ofNullable(dossierStore.get(id));
        });

        Map<UUID, List<SuiviEtape>> stepStore = new java.util.concurrent.ConcurrentHashMap<>();
        lenient().when(suiviEtapeRepository.saveAll(anyList())).thenAnswer(i -> {
            List<SuiviEtape> list = i.getArgument(0);
            if (!list.isEmpty() && list.get(0).getDossier() != null) {
                stepStore.put(list.get(0).getDossier().getId(), new ArrayList<>(list));
            }
            return list;
        });
        lenient().when(suiviEtapeRepository.save(any(SuiviEtape.class))).thenAnswer(i -> {
            SuiviEtape s = i.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        lenient().when(suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(any(UUID.class))).thenAnswer(i -> {
            UUID id = i.getArgument(0);
            return stepStore.getOrDefault(id, Collections.emptyList());
        });

        lenient().when(associeRepository.save(any(Associe.class))).thenAnswer(i -> {
            Associe a = i.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });

        lenient().when(gerantRepository.save(any(Gerant.class))).thenAnswer(i -> {
            Gerant g = i.getArgument(0);
            if (g.getId() == null) g.setId(UUID.randomUUID());
            return g;
        });
    }

    @Test
    @DisplayName("1. SARL - Rejet si capital social nul ou négatif")
    void testCreationSarl_RejetCapitalNul() {
        DossierCreateRequest request = new DossierCreateRequest(
                LegalFormCode.SARL,
                "Atlas Tech SARL",
                "Casablanca-Settat",
                "Développement de logiciels",
                BigDecimal.ZERO,
                "Boulevard d'Anfa, Casablanca",
                new BigDecimal("100.00"),
                List.of(),
                List.of()
        );

        InvalidDossierStateException ex = assertThrows(InvalidDossierStateException.class,
                () -> dossierService.createDossier(request, entrepreneur));
        assertTrue(ex.getMessage().contains("strictement supérieur à zéro"));
    }

    @Test
    @DisplayName("2. SARL - Rejet si valeur nominale des parts est nulle ou négative")
    void testCreationSarl_RejetValeurNominaleNegative() {
        DossierCreateRequest request = new DossierCreateRequest(
                LegalFormCode.SARL,
                "Atlas Tech SARL",
                "Casablanca-Settat",
                "Conseil IT",
                new BigDecimal("10000.00"),
                "Boulevard d'Anfa, Casablanca",
                BigDecimal.ZERO,
                List.of(),
                List.of()
        );

        InvalidDossierStateException ex = assertThrows(InvalidDossierStateException.class,
                () -> dossierService.createDossier(request, entrepreneur));
        assertTrue(ex.getMessage().contains("strictement positive"));
    }

    @Test
    @DisplayName("3. SARL - Rejet si capital n'est pas un multiple exact de la valeur nominale")
    void testCreationSarl_RejetCapitalNonDivisible() {
        DossierCreateRequest request = new DossierCreateRequest(
                LegalFormCode.SARL,
                "Atlas Tech SARL",
                "Casablanca-Settat",
                "Conseil IT",
                new BigDecimal("10050.00"),
                "Boulevard d'Anfa, Casablanca",
                new BigDecimal("100.00"),
                List.of(),
                List.of()
        );

        InvalidDossierStateException ex = assertThrows(InvalidDossierStateException.class,
                () -> dossierService.createDossier(request, entrepreneur));
        assertTrue(ex.getMessage().contains("multiple exact"));
    }

    @Test
    @DisplayName("4. SARL - Rejet si moins de 2 associés (séparation fonctionnelle SARL / SARL-AU)")
    void testCreationSarl_RejetMoinsDeDeuxAssocies() {
        AssocieDto a1 = new AssocieDto(null, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casablanca", 100L, null, null);

        DossierCreateRequest request = new DossierCreateRequest(
                LegalFormCode.SARL,
                "Atlas Tech SARL",
                "Casablanca-Settat",
                "Conseil IT",
                new BigDecimal("10000.00"),
                "Boulevard d'Anfa, Casablanca",
                new BigDecimal("100.00"),
                List.of(a1),
                List.of()
        );

        InvalidDossierStateException ex = assertThrows(InvalidDossierStateException.class,
                () -> dossierService.createDossier(request, entrepreneur));
        assertTrue(ex.getMessage().contains("entre 2 et 50 associés"));
    }

    @Test
    @DisplayName("5. SARL - Rejet si somme des parts != nombre total de parts")
    void testCreationSarl_RejetSommePartsDifferente() {
        // Total parts = 100 (10 000 / 100). Ici somme = 60 + 30 = 90
        AssocieDto a1 = new AssocieDto(null, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casablanca", 60L, null, null);
        AssocieDto a2 = new AssocieDto(null, "Benjelloun", "Omar", "CD789012", "Marocaine", "Rabat", 30L, null, null);

        DossierCreateRequest request = new DossierCreateRequest(
                LegalFormCode.SARL,
                "Atlas Tech SARL",
                "Casablanca-Settat",
                "Conseil IT",
                new BigDecimal("10000.00"),
                "Boulevard d'Anfa, Casablanca",
                new BigDecimal("100.00"),
                List.of(a1, a2),
                List.of()
        );

        InvalidDossierStateException ex = assertThrows(InvalidDossierStateException.class,
                () -> dossierService.createDossier(request, entrepreneur));
        assertTrue(ex.getMessage().contains("exactement égale au nombre total de parts"));
    }

    @Test
    @DisplayName("6. SARL - Rejet si aucun gérant désigné")
    void testCreationSarl_RejetSansGerant() {
        AssocieDto a1 = new AssocieDto(null, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casablanca", 60L, null, null);
        AssocieDto a2 = new AssocieDto(null, "Benjelloun", "Omar", "CD789012", "Marocaine", "Rabat", 40L, null, null);

        DossierCreateRequest request = new DossierCreateRequest(
                LegalFormCode.SARL,
                "Atlas Tech SARL",
                "Casablanca-Settat",
                "Conseil IT",
                new BigDecimal("10000.00"),
                "Boulevard d'Anfa, Casablanca",
                new BigDecimal("100.00"),
                List.of(a1, a2),
                List.of() // Aucun gérant
        );

        InvalidDossierStateException ex = assertThrows(InvalidDossierStateException.class,
                () -> dossierService.createDossier(request, entrepreneur));
        assertTrue(ex.getMessage().contains("Au moins un gérant doit être désigné"));
    }

    @Test
    @DisplayName("7. SARL - Rejet si gérant associé ne correspond à aucun associé du dossier")
    void testCreationSarl_RejetGerantAssocieInexistant() {
        AssocieDto a1 = new AssocieDto(null, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casablanca", 60L, null, null);
        AssocieDto a2 = new AssocieDto(null, "Benjelloun", "Omar", "CD789012", "Marocaine", "Rabat", 40L, null, null);

        GerantDto g1 = new GerantDto(null, null, true, "Inconnu", "Tiers", "ZZ999999", "Marocaine", "Fes", ModeNominationGerant.STATUTAIRE, "Indéterminée", null);

        DossierCreateRequest request = new DossierCreateRequest(
                LegalFormCode.SARL,
                "Atlas Tech SARL",
                "Casablanca-Settat",
                "Conseil IT",
                new BigDecimal("10000.00"),
                "Boulevard d'Anfa, Casablanca",
                new BigDecimal("100.00"),
                List.of(a1, a2),
                List.of(g1)
        );

        InvalidDossierStateException ex = assertThrows(InvalidDossierStateException.class,
                () -> dossierService.createDossier(request, entrepreneur));
        assertTrue(ex.getMessage().contains("correspondre à un associé du même dossier"));
    }

    @Test
    @DisplayName("8. SARL - Création réussie avec 2 associés et 1 gérant associé (calcul apports et pourcentages)")
    void testCreationSarl_SuccesDeuxAssociesEtGerantAssocie() {
        AssocieDto a1 = new AssocieDto(null, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casablanca", 60L, null, null);
        AssocieDto a2 = new AssocieDto(null, "Benjelloun", "Omar", "CD789012", "Marocaine", "Rabat", 40L, null, null);

        GerantDto g1 = new GerantDto(null, null, true, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casablanca", ModeNominationGerant.STATUTAIRE, "Indéterminée", "Tous pouvoirs");

        DossierCreateRequest request = new DossierCreateRequest(
                LegalFormCode.SARL,
                "Atlas Tech SARL",
                "Casablanca-Settat",
                "Conseil IT",
                new BigDecimal("10000.00"),
                "Boulevard d'Anfa, Casablanca",
                new BigDecimal("100.00"),
                List.of(a1, a2),
                List.of(g1)
        );

        DossierDetailDto result = dossierService.createDossier(request, entrepreneur);

        assertNotNull(result);
        assertEquals(LegalFormCode.SARL, result.legalFormCode());
        assertEquals(new BigDecimal("10000.00"), result.capitalAmountMad());
        assertEquals(new BigDecimal("100.00"), result.valeurNominalePart());
        assertEquals(100L, result.nombreTotalParts());
        assertEquals(2, result.associes().size());
        assertEquals(1, result.gerants().size());

        AssocieDto resA1 = result.associes().stream().filter(a -> a.cinOuPasseport().equals("AB123456")).findFirst().orElseThrow();
        assertEquals(60L, resA1.nombreParts());
        assertEquals(new BigDecimal("6000.00"), resA1.montantApport());
        assertEquals(new BigDecimal("60.00"), resA1.pourcentageDetention());

        AssocieDto resA2 = result.associes().stream().filter(a -> a.cinOuPasseport().equals("CD789012")).findFirst().orElseThrow();
        assertEquals(40L, resA2.nombreParts());
        assertEquals(new BigDecimal("4000.00"), resA2.montantApport());
        assertEquals(new BigDecimal("40.00"), resA2.pourcentageDetention());

        GerantDto resG1 = result.gerants().get(0);
        assertTrue(resG1.estAssocie());
        assertEquals("El Amrani", resG1.nom());
        assertEquals("AB123456", resG1.cinOuPasseport());
    }

    @Test
    @DisplayName("9. SARL - RG15 : Dispense légale de blocage bancaire si capital <= 100 000 MAD")
    void testBlocageCapital_DispenseMoinsCentMille() {
        AssocieDto a1 = new AssocieDto(null, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casablanca", 300L, null, null);
        AssocieDto a2 = new AssocieDto(null, "Benjelloun", "Omar", "CD789012", "Marocaine", "Rabat", 200L, null, null);
        GerantDto g1 = new GerantDto(null, null, true, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casablanca", ModeNominationGerant.STATUTAIRE, "Indéterminée", null);

        DossierCreateRequest request = new DossierCreateRequest(
                LegalFormCode.SARL,
                "Atlas Tech SARL",
                "Casablanca-Settat",
                "Conseil IT",
                new BigDecimal("50000.00"), // <= 100 000 MAD
                "Boulevard d'Anfa, Casablanca",
                new BigDecimal("100.00"),
                List.of(a1, a2),
                List.of(g1)
        );

        DossierDetailDto result = dossierService.createDossier(request, entrepreneur);
        assertFalse(result.capitalBlockageRequired());

        // Vérifier dans les étapes que l'étape 3 (BLOCAGE_CAPITAL) est VALIDEE d'office
        StepTrackingDto step3 = result.steps().stream().filter(s -> "BLOCAGE_CAPITAL".equals(s.code())).findFirst().orElseThrow();
        assertEquals(StepStatus.VALIDEE, step3.status());
    }

    @Test
    @DisplayName("10. SARL - RG15 : Attestation obligatoire si capital > 100 000 MAD")
    void testBlocageCapital_ObligatoirePlusCentMille() {
        AssocieDto a1 = new AssocieDto(null, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casablanca", 900L, null, null);
        AssocieDto a2 = new AssocieDto(null, "Benjelloun", "Omar", "CD789012", "Marocaine", "Rabat", 600L, null, null);
        GerantDto g1 = new GerantDto(null, null, true, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casablanca", ModeNominationGerant.STATUTAIRE, "Indéterminée", null);

        DossierCreateRequest request = new DossierCreateRequest(
                LegalFormCode.SARL,
                "Atlas Tech SARL",
                "Casablanca-Settat",
                "Conseil IT",
                new BigDecimal("150000.00"), // > 100 000 MAD
                "Boulevard d'Anfa, Casablanca",
                new BigDecimal("100.00"),
                List.of(a1, a2),
                List.of(g1)
        );

        DossierDetailDto result = dossierService.createDossier(request, entrepreneur);
        assertTrue(result.capitalBlockageRequired());

        StepTrackingDto step3 = result.steps().stream().filter(s -> "BLOCAGE_CAPITAL".equals(s.code())).findFirst().orElseThrow();
        assertEquals(StepStatus.EN_ATTENTE, step3.status());
    }

    @Test
    @DisplayName("11. SARL Checklist - Exigence individuelle des CIN pour associés et gérant tiers (sans doublon pour gérant associé)")
    void testChecklist_CinIndividuellesEtPasDeDoublon() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.SARL);
        dossier.setStatut(DossierStatus.BROUILLON);
        dossier.setEntrepreneur(entrepreneur);

        Associe a1 = new Associe(dossier, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casa", 60L, new BigDecimal("6000"), new BigDecimal("60"));
        a1.setId(UUID.randomUUID());
        a1.setActif(true);

        Associe a2 = new Associe(dossier, "Benjelloun", "Omar", "CD789012", "Marocaine", "Rabat", 40L, new BigDecimal("4000"), new BigDecimal("40"));
        a2.setId(UUID.randomUUID());
        a2.setActif(true);

        dossier.setAssocies(List.of(a1, a2));

        // Gérant 1 : associé (a1) -> pas de doublon CIN
        Gerant g1 = new Gerant(dossier, a1, true, a1.getNom(), a1.getPrenom(), a1.getCinOuPasseport(), a1.getNationalite(), a1.getAdresse(), ModeNominationGerant.STATUTAIRE, "Indéterminée", null);
        g1.setId(UUID.randomUUID());
        g1.setActif(true);

        // Gérant 2 : tiers non associé -> CIN tiers requise
        Gerant g2 = new Gerant(dossier, null, false, "Kabbaj", "Mehdi", "EF345678", "Marocaine", "Tanger", ModeNominationGerant.STATUTAIRE, "Indéterminée", null);
        g2.setId(UUID.randomUUID());
        g2.setActif(true);

        dossier.setGerants(List.of(g1, g2));

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(documentRepository.findByDossierId(dossierId)).thenReturn(List.of());

        List<ChecklistItemDto> checklist = documentService.getChecklist(dossierId, entrepreneur);

        // Doit contenir 3 CIN au total : a1, a2, g2
        List<ChecklistItemDto> cinItems = checklist.stream().filter(c -> "CIN".equals(c.documentTypeCode())).toList();
        assertEquals(3, cinItems.size());

        assertTrue(cinItems.stream().anyMatch(c -> a1.getId().equals(c.associeId())));
        assertTrue(cinItems.stream().anyMatch(c -> a2.getId().equals(c.associeId())));
        assertTrue(cinItems.stream().anyMatch(c -> g2.getId().equals(c.gerantId())));
    }

    @Test
    @DisplayName("12. SARL Checklist - PV_NOMINATION_GERANT obligatoire seulement si gérant en ACTE_SEPARE")
    void testChecklist_PvNominationConditionnel() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.SARL);
        dossier.setStatut(DossierStatus.BROUILLON);
        dossier.setEntrepreneur(entrepreneur);

        Associe a1 = new Associe(dossier, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casa", 60L, new BigDecimal("6000"), new BigDecimal("60"));
        a1.setId(UUID.randomUUID()); a1.setActif(true);
        Associe a2 = new Associe(dossier, "Benjelloun", "Omar", "CD789012", "Marocaine", "Rabat", 40L, new BigDecimal("4000"), new BigDecimal("40"));
        a2.setId(UUID.randomUUID()); a2.setActif(true);
        dossier.setAssocies(List.of(a1, a2));

        // Cas 1 : Nomination STATUTAIRE -> pas de PV exigé
        Gerant gStat = new Gerant(dossier, a1, true, a1.getNom(), a1.getPrenom(), a1.getCinOuPasseport(), a1.getNationalite(), a1.getAdresse(), ModeNominationGerant.STATUTAIRE, "Indéterminée", null);
        dossier.setGerants(List.of(gStat));

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(documentRepository.findByDossierId(dossierId)).thenReturn(List.of());

        List<ChecklistItemDto> checkStat = documentService.getChecklist(dossierId, entrepreneur);
        assertFalse(checkStat.stream().anyMatch(c -> "PV_NOMINATION_GERANT".equals(c.documentTypeCode())));

        // Cas 2 : Nomination ACTE_SEPARE -> PV exigé
        gStat.setModeNomination(ModeNominationGerant.ACTE_SEPARE);
        List<ChecklistItemDto> checkSepare = documentService.getChecklist(dossierId, entrepreneur);
        assertTrue(checkSepare.stream().anyMatch(c -> "PV_NOMINATION_GERANT".equals(c.documentTypeCode()) && c.required()));
    }

    @Test
    @DisplayName("13. Documents - Rejet upload si exclusivité violée (associeId et gerantId fournis simultanément)")
    void testUpload_RejetExclusivite() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setEntrepreneur(entrepreneur);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        MockMultipartFile file = new MockMultipartFile("file", "cin.pdf", "application/pdf", new byte[]{1, 2, 3});

        InvalidDossierStateException ex = assertThrows(InvalidDossierStateException.class,
                () -> documentService.uploadDocument(dossierId, file, "CIN", "Note", UUID.randomUUID(), UUID.randomUUID(), entrepreneur));
        assertTrue(ex.getMessage().contains("simultanément"));
    }

    @Test
    @DisplayName("14. Documents - Rejet upload si associeId appartient à un autre dossier (403 / Étanchéité)")
    void testUpload_RejetEtancheiteInterDossiers() {
        UUID dossierId1 = UUID.randomUUID();
        UUID dossierId2 = UUID.randomUUID();

        Dossier d1 = new Dossier(); d1.setId(dossierId1); d1.setEntrepreneur(entrepreneur);
        Dossier d2 = new Dossier(); d2.setId(dossierId2);

        Associe associeD2 = new Associe(d2, "Autre", "Personne", "XX111111", "Marocaine", "Rabat", 10L, new BigDecimal("1000"), new BigDecimal("10"));
        UUID associeId = UUID.randomUUID();
        associeD2.setId(associeId);

        when(dossierRepository.findById(dossierId1)).thenReturn(Optional.of(d1));
        when(associeRepository.findById(associeId)).thenReturn(Optional.of(associeD2));

        MockMultipartFile file = new MockMultipartFile("file", "cin.pdf", "application/pdf", new byte[]{1, 2, 3});

        UnauthorizedDossierAccessException ex = assertThrows(UnauthorizedDossierAccessException.class,
                () -> documentService.uploadDocument(dossierId1, file, "CIN", "Note", associeId, null, entrepreneur));
        assertTrue(ex.getMessage().contains("n'appartient pas à ce dossier"));
    }

    @Test
    @DisplayName("15. RG12 & Traçabilité - Retrait d'un associé en modification : désactivation logique et événement AuditEvent")
    void testModification_RetraitAssocie_DesactivationLogiqueEtAudit() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.SARL);
        dossier.setStatut(DossierStatus.BROUILLON);
        dossier.setCapitalMad(new BigDecimal("10000.00"));
        dossier.setValeurNominalePart(new BigDecimal("100.00"));
        dossier.setNombreTotalParts(100L);
        dossier.setEntrepreneur(entrepreneur);

        Associe a1 = new Associe(dossier, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casa", 50L, new BigDecimal("5000"), new BigDecimal("50"));
        a1.setId(UUID.randomUUID()); a1.setActif(true);

        Associe a2 = new Associe(dossier, "Ancien", "Partenaire", "OLD123", "Marocaine", "Casa", 50L, new BigDecimal("5000"), new BigDecimal("50"));
        a2.setId(UUID.randomUUID()); a2.setActif(true);

        dossier.setAssocies(new ArrayList<>(List.of(a1, a2)));

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(associeRepository.findByDossierId(dossierId)).thenReturn(List.of(a1, a2));

        // Requête de mise à jour retirant a2 et remplaçant par a3 (nouveau)
        AssocieDto a1Dto = new AssocieDto(a1.getId(), "El Amrani", "Yassine", "AB123456", "Marocaine", "Casa", 60L, null, null);
        AssocieDto a3Dto = new AssocieDto(null, "Nouveau", "Associe", "NEW789", "Marocaine", "Casa", 40L, null, null);

        GerantDto g1Dto = new GerantDto(null, null, true, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casa", ModeNominationGerant.STATUTAIRE, "Indéterminée", null);

        DossierUpdateRequest updateReq = new DossierUpdateRequest(
                LegalFormCode.SARL,
                "Atlas Tech SARL",
                "Casablanca-Settat",
                "IT",
                new BigDecimal("10000.00"),
                "Boulevard d'Anfa, Casablanca",
                new BigDecimal("100.00"),
                List.of(a1Dto, a3Dto),
                List.of(g1Dto)
        );

        dossierService.updateDossier(dossierId, updateReq, entrepreneur);

        // a2 doit avoir été désactivé logiquement (setActif(false)), et NON supprimé physiquement
        assertFalse(a2.isActif());

        // Vérifier qu'un AuditEvent DOSSIER_MODIFIE a été consigné pour le retrait
        verify(auditEventRepository, atLeastOnce()).save(argThat(event ->
                event.getAction() == AuditEventAction.DOSSIER_MODIFIE
                        && event.getDetails() != null && event.getDetails().contains("Retrait de l'associé")
        ));
    }

    @Test
    @DisplayName("16. Verrouillage RG2 - Interdiction de modification sur dossier soumis ou finalisé")
    void testModification_InterdictionApresSoumission() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.SARL);
        dossier.setStatut(DossierStatus.SOUMIS); // Non modifiable
        dossier.setEntrepreneur(entrepreneur);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        DossierUpdateRequest req = new DossierUpdateRequest(
                LegalFormCode.SARL, "Nouveau Nom", "Rabat", "Desc",
                new BigDecimal("10000.00"), "Adresse", new BigDecimal("100.00"), List.of(), List.of()
        );

        InvalidDossierStateException ex = assertThrows(InvalidDossierStateException.class,
                () -> dossierService.updateDossier(dossierId, req, entrepreneur));
        assertTrue(ex.getMessage().contains("n'est permise qu'aux statuts Brouillon et À corriger"));
    }

    @Test
    @DisplayName("17. Guichet CRI - Rejet validation si un associé actif n'a pas de CIN conforme")
    void testAgentCri_RejetSiCinAssocieNonConforme() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = new Dossier();
        dossier.setId(dossierId);
        dossier.setFormeJuridique(LegalFormCode.SARL);
        dossier.setStatut(DossierStatus.EN_COURS);
        dossier.setCapitalBlockageRequired(false);

        SuiviEtape stepCri = new SuiviEtape();
        stepCri.setId(UUID.randomUUID());
        stepCri.setEtapeCode("DEPOT_CRI");
        stepCri.setOrganismeCode("CRI");
        stepCri.setStatut(StepStatus.EN_COURS);
        stepCri.setActionOwner(ActorType.AGENT);
        stepCri.setAgent(agentCri);
        stepCri.setClaimedByAgent(agentCri);
        stepCri.setDossier(dossier);

        dossier.setSteps(List.of(stepCri));

        Associe a1 = new Associe(dossier, "El Amrani", "Yassine", "AB123456", "Marocaine", "Casa", 60L, new BigDecimal("6000"), new BigDecimal("60"));
        a1.setId(UUID.randomUUID()); a1.setActif(true);

        Associe a2 = new Associe(dossier, "Benjelloun", "Omar", "CD789012", "Marocaine", "Rabat", 40L, new BigDecimal("4000"), new BigDecimal("40"));
        a2.setId(UUID.randomUUID()); a2.setActif(true);

        dossier.setAssocies(List.of(a1, a2));

        // Documents : CIN pour a1 CONFORME, mais CIN pour a2 manquante ou NON_CONFORME
        DocumentEntity docA1 = new DocumentEntity();
        docA1.setDocumentTypeCode("CIN");
        docA1.setAssocie(a1);
        docA1.setReviewStatus(DocumentReviewStatus.CONFORME);

        DocumentEntity docSiege = new DocumentEntity();
        docSiege.setDocumentTypeCode("JUSTIFICATIF_SIEGE");
        docSiege.setReviewStatus(DocumentReviewStatus.CONFORME);

        DocumentEntity docStatuts = new DocumentEntity();
        docStatuts.setDocumentTypeCode("STATUTS_SIGNES");
        docStatuts.setReviewStatus(DocumentReviewStatus.CONFORME);

        DocumentEntity docForm = new DocumentEntity();
        docForm.setDocumentTypeCode("FORMULAIRE_UNIQUE");
        docForm.setReviewStatus(DocumentReviewStatus.CONFORME);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(suiviEtapeRepository.findFirstByDossierIdAndStatutOrderByOrdreAsc(dossierId, StepStatus.EN_COURS)).thenReturn(Optional.of(stepCri));
        when(documentRepository.findByDossierIdOrderByUploadedAtDesc(dossierId))
                .thenReturn(List.of(docA1, docSiege, docStatuts, docForm));

        AgentValidateRequest valReq = new AgentValidateRequest(
                "Notes",
                Map.of("companyFileComplete", true, "companyDetailsConsistent", true, "documentsComplete", true),
                Map.of("depositReceiptNumber", "REC-2026-999")
        );

        InvalidDossierStateException ex = assertThrows(InvalidDossierStateException.class,
                () -> agentService.validateStep(dossierId, valReq, agentCri));
        assertTrue(ex.getMessage().contains("Omar Benjelloun") && ex.getMessage().contains("doit être contrôlée et conforme"));
    }
}
