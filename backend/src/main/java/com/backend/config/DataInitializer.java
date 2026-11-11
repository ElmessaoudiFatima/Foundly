package com.backend.config;

import com.backend.entity.Dossier;
import com.backend.entity.AuditEvent;
import com.backend.entity.DocumentEntity;
import com.backend.entity.EtapeWorkflow;
import com.backend.entity.Organisme;
import com.backend.entity.SuiviEtape;
import com.backend.entity.Utilisateur;
import com.backend.entity.WorkflowVersion;
import com.backend.enums.ActorType;
import com.backend.enums.DossierStatus;
import com.backend.enums.DocumentSource;
import com.backend.enums.AuditEventAction;
import com.backend.enums.LegalFormCode;
import com.backend.enums.RoleUtilisateur;
import com.backend.enums.StepStatus;
import com.backend.enums.WorkflowStatus;
import com.backend.repository.DossierRepository;
import com.backend.repository.AuditEventRepository;
import com.backend.repository.DocumentRepository;
import com.backend.repository.EtapeWorkflowRepository;
import com.backend.repository.OrganismeRepository;
import com.backend.repository.SuiviEtapeRepository;
import com.backend.repository.UtilisateurRepository;
import com.backend.repository.WorkflowVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${foundly.upload.dir:./uploads}")
    private String uploadBaseDir;

    @Bean
    public CommandLineRunner initDatabase(
            UtilisateurRepository utilisateurRepository,
            DossierRepository dossierRepository,
            DocumentRepository documentRepository,
            AuditEventRepository auditEventRepository,
            OrganismeRepository organismeRepository,
            SuiviEtapeRepository suiviEtapeRepository,
            PasswordEncoder passwordEncoder,
            WorkflowVersionRepository workflowVersionRepository,
            EtapeWorkflowRepository etapeWorkflowRepository
    ) {
        return args -> runInit(
                utilisateurRepository, dossierRepository, documentRepository, auditEventRepository, organismeRepository,
                suiviEtapeRepository, passwordEncoder,
                workflowVersionRepository, etapeWorkflowRepository
        );
    }

    @Transactional
    public void runInit(
            UtilisateurRepository utilisateurRepository,
            DossierRepository dossierRepository,
            DocumentRepository documentRepository,
            AuditEventRepository auditEventRepository,
            OrganismeRepository organismeRepository,
            SuiviEtapeRepository suiviEtapeRepository,
            PasswordEncoder passwordEncoder,
            WorkflowVersionRepository workflowVersionRepository,
            EtapeWorkflowRepository etapeWorkflowRepository
    ) {
        log.info("=== Foundly DataInitializer: debut du seed ===");

        // 0. Seed organismes partenaires (idempotent)
        seedOrganismeIfMissing(organismeRepository, "OMPIC",
                "Office Marocain de la Propriete Industrielle et Commerciale",
                "Delivre le certificat negatif attestant la disponibilite du nom commercial.");
        seedOrganismeIfMissing(organismeRepository, "CRI",
                "Centres Regionaux d'Investissement",
                "Guichet unique regional qui receptionne et traite les dossiers de creation d'entreprise.");
        seedOrganismeIfMissing(organismeRepository, "TRIBUNAL",
                "Tribunaux de commerce",
                "Prononce l'immatriculation de la societe au Registre du Commerce et des Societes.");
        seedOrganismeIfMissing(organismeRepository, "DGI",
                "Direction Generale des Impots",
                "Attribue l'Identifiant Fiscal (IF) et la patente necessaires a l'exercice de l'activite.");
        seedOrganismeIfMissing(organismeRepository, "CNSS",
                "Caisse Nationale de Securite Sociale",
                "Enregistre l'entreprise comme employeur pour la couverture sociale des salaries.");
        seedOrganismeIfMissing(organismeRepository, "JOURNAL",
                "Journaux d'annonces legales",
                "Publie l'avis de constitution de la societe conformement aux obligations legales.");

        // 1. Administrateur principal
        if (!utilisateurRepository.existsByEmail("admin@foundly.ma")) {
            Utilisateur admin = new Utilisateur();
            admin.setNom("Benjelloun");
            admin.setPrenom("Samira");
            admin.setEmail("admin@foundly.ma");
            admin.setPassword(passwordEncoder.encode("Admin123456!"));
            admin.setRole(RoleUtilisateur.ADMINISTRATEUR);
            admin.setActif(true);
            admin.setCin("BE458921");
            admin.setTelephone("0661234567");
            admin.setOrganisme("Administration centrale");
            admin.setLastActivityAt(Instant.now());
            utilisateurRepository.save(admin);
        }

        // 2. Agents des organismes
        seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                "Khadija", "Berrada", "khadija.berrada@cri.ma", "CRI", true, Instant.now().minus(30, ChronoUnit.MINUTES));
        seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                "Nadia", "El Fassi", "nadia.elfassi@ompic.ma", "OMPIC", true, Instant.now().minus(2, ChronoUnit.HOURS));
        seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                "Youssef", "Karim", "y.karim@tribunal.ma", "TRIBUNAL", true, Instant.now().minus(1, ChronoUnit.DAYS));
        seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                "Leila", "Amrani", "l.amrani@dgi.gov.ma", "DGI", false, Instant.now().minus(15, ChronoUnit.DAYS));
        seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                "Karim", "Bennis", "k.bennis@cnss.ma", "CNSS", true, Instant.now().minus(3, ChronoUnit.HOURS));

        // Migration : normaliser les codes organisme de tous les agents existants
        migrateAgentOrganismeToCode(utilisateurRepository);

        // 3. Entrepreneurs et dossiers initiaux
        if (dossierRepository.count() == 0) {
            Utilisateur ent1 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Aya", "El Mansouri", "aya.mansouri@gmail.com", "BK789456", "0612345678");
            Utilisateur ent2 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Salma", "Idrissi", "salma.idrissi@gmail.com", "AB654321", "0623456789");
            Utilisateur ent3 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Youssef", "Amrani", "youssef.amrani@gmail.com", "C987654", "0634567890");
            Utilisateur ent4 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Imane", "Dennis", "imane.dennis@gmail.com", "EE123456", "0645678901");
            Utilisateur ent5 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Omar", "Alaoui", "omar.alaoui@gmail.com", "F543210", "0656789012");
            Utilisateur ent6 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Mehdi", "Tazi", "mehdi.tazi@gmail.com", "CD112233", "0677889900");
            Utilisateur ent7 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Kenza", "Bennani", "kenza.bennani@gmail.com", "G445566", "0699001122");

            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04821", "Atlas Studio", LegalFormCode.SARL,
                    DossierStatus.EN_COURS, "Casablanca-Settat", "Controle CRI", new BigDecimal("100000"), ent1, 8, null);
            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04836", "Maison Zayna", LegalFormCode.SARL_AU,
                    DossierStatus.EN_COURS, "Rabat-Sale-Kenitra", "Registre de commerce", new BigDecimal("50000"), ent2, 6, null);
            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04812", "Nexa Conseil", LegalFormCode.SARL,
                    DossierStatus.A_CORRIGER, "Casablanca-Settat", "Controle CRI", new BigDecimal("150000"), ent3, 10,
                    "Attestation de blocage du capital non conforme.");
            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04798", "Terres d'Atlas", LegalFormCode.SA,
                    DossierStatus.FINALISE, "Marrakech-Safi", "Publication legale", new BigDecimal("350000"), ent4, 16, null);
            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04781", "Riad Digital", LegalFormCode.AUTO_ENTREPRENEUR,
                    DossierStatus.FINALISE, "Fes-Meknes", "Immatriculation", BigDecimal.ZERO, ent5, 20, null);
            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04850", "MedTech Solutions", LegalFormCode.SARL_AU,
                    DossierStatus.SOUMIS, "Tanger-Tetouan-Al Hoceima", "Certificat negatif", new BigDecimal("80000"), ent6, 2, null);
            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04862", "EcoTransport Maghreb", LegalFormCode.SARL,
                    DossierStatus.BROUILLON, "Casablanca-Settat", "Constitution dossier", new BigDecimal("200000"), ent7, 1, null);

            log.info("=== Foundly DataInitializer: {} dossiers seedes avec succes ===", dossierRepository.count());
        } else {
            log.info("=== Foundly DataInitializer: dossiers deja presents ({}) ===", dossierRepository.count());
        }

        // 3.5. Synchronisation active des étapes de workflow avec l'étape courante
        synchronizeDossierStepsWithCurrentState(dossierRepository, suiviEtapeRepository);
        ensureSeededDossierData(dossierRepository, suiviEtapeRepository, documentRepository,
                auditEventRepository, utilisateurRepository);

        // 4. Seed workflows par defaut (idempotent)
        seedWorkflowsIfMissing(workflowVersionRepository, etapeWorkflowRepository);

        log.info("=== Foundly DataInitializer: termine avec succes ===");
    }

    // =========================================================
    // SYNCHRONISATION DES ÉTAPES DU WORKFLOW
    // =========================================================

    /**
     * S'assure que chaque dossier en cours ou soumis a son étape active
     * (StepStatus.EN_COURS) exactement alignée sur son organisme de traitement,
     * permettant aux agents respectifs (OMPIC, CRI, Tribunal) de voir leurs dossiers en file d'attente.
     */
    private void synchronizeDossierStepsWithCurrentState(
            DossierRepository dossierRepo,
            SuiviEtapeRepository stepRepo
    ) {
        List<Dossier> dossiers = dossierRepo.findAll();
        for (Dossier d : dossiers) {
            if (d.getStatut() == DossierStatus.FINALISE || d.getStatut() == DossierStatus.BROUILLON) {
                continue;
            }
            List<SuiviEtape> steps = stepRepo.findByDossierIdOrderByOrdreAsc(d.getId());
            if (steps.isEmpty()) {
                steps = buildStepsForDossier(d);
                steps = stepRepo.saveAll(steps);
            }

            if (d.getStatut() == DossierStatus.A_CORRIGER) {
                List<SuiviEtape> currentSteps = steps;
                SuiviEtape rejectedStep = steps.stream()
                        .filter(step -> step.getStatut() == StepStatus.REJETEE)
                        .findFirst()
                        .orElseGet(() -> currentSteps.stream()
                                .filter(step -> step.getOrdre() == 4)
                                .findFirst()
                                .orElse(currentSteps.get(0)));
                for (SuiviEtape step : steps) {
                    if (step.getOrdre() < rejectedStep.getOrdre()) {
                        step.setStatut(StepStatus.VALIDEE);
                        if (step.getDecidedAt() == null) step.setDecidedAt(Instant.now().minus(2, ChronoUnit.DAYS));
                    } else if (step.getId().equals(rejectedStep.getId())) {
                        step.setStatut(StepStatus.REJETEE);
                        step.setMotifRejet(d.getRejectionReason());
                        if (step.getDecidedAt() == null) step.setDecidedAt(Instant.now().minus(1, ChronoUnit.DAYS));
                        d.setCurrentStepCode(step.getEtapeCode());
                        d.setCurrentStepOrder(step.getOrdre());
                        d.setEtapeActuelle(step.getLibelle());
                    } else {
                        step.setStatut(StepStatus.EN_ATTENTE);
                    }
                }
                stepRepo.saveAll(steps);
                dossierRepo.save(d);
                continue;
            }

            String nom = d.getNomEntreprise() != null ? d.getNomEntreprise().toLowerCase() : "";
            String ref = d.getReference() != null ? d.getReference().toUpperCase() : "";
            String etape = d.getEtapeActuelle() != null ? d.getEtapeActuelle().toLowerCase() : "";
            String code = d.getCurrentStepCode() != null ? d.getCurrentStepCode().toUpperCase() : "";

            // Cas A : Dossier Guichet CRI (ex: Atlas Studio ou Nexa Conseil) -> ordre 4 (DEPOT_CRI)
            if (nom.contains("atlas") || ref.contains("04821") || etape.contains("cri") || code.contains("CRI")) {
                for (SuiviEtape s : steps) {
                    if (s.getOrdre() < 4) {
                        s.setStatut(StepStatus.VALIDEE);
                        if (s.getDecidedAt() == null) s.setDecidedAt(Instant.now().minus(2, ChronoUnit.DAYS));
                    } else if (s.getOrdre() == 4) {
                        s.setStatut(StepStatus.EN_COURS);
                        s.setOrganismeCode("CRI");
                        if (s.getStartedAt() == null) s.setStartedAt(Instant.now().minus(1, ChronoUnit.DAYS));
                        d.setCurrentStepCode(s.getEtapeCode());
                        d.setCurrentStepOrder(s.getOrdre());
                        d.setEtapeActuelle(s.getLibelle());
                    } else {
                        s.setStatut(StepStatus.EN_ATTENTE);
                    }
                }
                stepRepo.saveAll(steps);
                dossierRepo.save(d);
                log.info("Dossier {} ({}) synchronise a l'etape 4 (CRI)", d.getReference(), d.getNomEntreprise());
            }
            // Cas B : Dossier Tribunal de Commerce (ex: Maison Zayna) -> ordre 5 (REGISTRE_COMMERCE Tribunal)
            else if (nom.contains("zayna") || ref.contains("04836") || etape.contains("registre") || etape.contains("tribunal") || code.contains("COMMERCE") || code.contains("TRIBUNAL")) {
                for (SuiviEtape s : steps) {
                    if (s.getOrdre() < 5) {
                        s.setStatut(StepStatus.VALIDEE);
                        if (s.getDecidedAt() == null) s.setDecidedAt(Instant.now().minus(2, ChronoUnit.DAYS));
                    } else if (s.getOrdre() == 5) {
                        s.setStatut(StepStatus.EN_COURS);
                        s.setOrganismeCode("TRIBUNAL");
                        if (s.getStartedAt() == null) s.setStartedAt(Instant.now().minus(1, ChronoUnit.DAYS));
                        d.setCurrentStepCode(s.getEtapeCode());
                        d.setCurrentStepOrder(s.getOrdre());
                        d.setEtapeActuelle(s.getLibelle());
                    } else {
                        s.setStatut(StepStatus.EN_ATTENTE);
                    }
                }
                stepRepo.saveAll(steps);
                dossierRepo.save(d);
                log.info("Dossier {} ({}) synchronise a l'etape 5 (TRIBUNAL)", d.getReference(), d.getNomEntreprise());
            }
            // Cas C : Dossier OMPIC (ex: MedTech Solutions) -> ordre 1 (CERTIFICAT_NEGATIF)
            else if (nom.contains("medtech") || ref.contains("04850") || etape.contains("certificat") || etape.contains("negatif") || code.contains("NEGATIF") || code.contains("OMPIC")) {
                for (SuiviEtape s : steps) {
                    if (s.getOrdre() == 1) {
                        s.setStatut(StepStatus.EN_COURS);
                        s.setOrganismeCode("OMPIC");
                        if (s.getStartedAt() == null) s.setStartedAt(Instant.now().minus(1, ChronoUnit.DAYS));
                        d.setCurrentStepCode(s.getEtapeCode());
                        d.setCurrentStepOrder(s.getOrdre());
                        d.setEtapeActuelle(s.getLibelle());
                    } else if (s.getOrdre() > 1 && s.getStatut() == StepStatus.EN_COURS) {
                        s.setStatut(StepStatus.EN_ATTENTE);
                    }
                }
                stepRepo.saveAll(steps);
                dossierRepo.save(d);
                log.info("Dossier {} ({}) synchronise a l'etape 1 (OMPIC)", d.getReference(), d.getNomEntreprise());
            }
        }
    }

    private void ensureSeededDossierData(
            DossierRepository dossierRepository,
            SuiviEtapeRepository stepRepository,
            DocumentRepository documentRepository,
            AuditEventRepository auditEventRepository,
            UtilisateurRepository utilisateurRepository
    ) {
        List<Utilisateur> agents = utilisateurRepository.findByRole(RoleUtilisateur.AGENT);
        for (Dossier dossier : dossierRepository.findAll()) {
            if (!dossier.getReference().startsWith("FND-2025-048")) continue;
            if (dossier.getSubmittedAt() == null && dossier.getStatut() != DossierStatus.BROUILLON) {
                dossier.setSubmittedAt(dossier.getCreatedAt() != null
                        ? dossier.getCreatedAt()
                        : Instant.now().minus(1, ChronoUnit.DAYS));
                dossierRepository.save(dossier);
            }

            List<SuiviEtape> steps = stepRepository.findByDossierIdOrderByOrdreAsc(dossier.getId());
            Set<UUID> auditedSteps = new HashSet<>();
            auditEventRepository.findByDossierIdOrderByOccurredAtDesc(dossier.getId()).stream()
                    .map(AuditEvent::getStepTrackingId)
                    .filter(java.util.Objects::nonNull)
                    .forEach(auditedSteps::add);

            for (SuiviEtape step : steps) {
                if ((step.getStatut() != StepStatus.VALIDEE && step.getStatut() != StepStatus.REJETEE)
                        || step.getActionOwner() != ActorType.AGENT) continue;

                Utilisateur agent = agents.stream()
                        .filter(candidate -> step.getOrganismeCode() != null
                                && step.getOrganismeCode().equalsIgnoreCase(candidate.getOrganisme()))
                        .findFirst()
                        .orElse(null);
                if (step.getAgent() == null && agent != null) step.setAgent(agent);
                if (step.getDecidedAt() == null) step.setDecidedAt(Instant.now().minus(2, ChronoUnit.DAYS));
                stepRepository.save(step);

                if (!auditedSteps.contains(step.getId())) {
                    AuditEventAction action = step.getStatut() == StepStatus.REJETEE
                            ? AuditEventAction.ETAPE_REJETEE
                            : AuditEventAction.ETAPE_VALIDEE;
                    String actorName = agent == null ? "Agent " + step.getOrganismeCode()
                            : agent.getPrenom() + " " + agent.getNom();
                    auditEventRepository.save(new AuditEvent(
                            dossier,
                            action,
                            ActorType.AGENT,
                            agent,
                            actorName,
                            step.getId(),
                            step.getMotifRejet(),
                            "Décision de démonstration — " + step.getLibelle()
                    ));
                    auditedSteps.add(step.getId());
                }
            }

            if (dossier.getStatut() != DossierStatus.EN_COURS
                    && dossier.getStatut() != DossierStatus.SOUMIS
                    && dossier.getStatut() != DossierStatus.A_CORRIGER) continue;
            List<String> documentTypes = switch (dossier.getCurrentStepCode() == null ? "" : dossier.getCurrentStepCode()) {
                case "CERTIFICAT_NEGATIF" -> List.of("CIN", "JUSTIFICATIF_SIEGE");
                case "DEPOT_CRI" -> {
                    List<String> types = new ArrayList<>(List.of("CIN", "JUSTIFICATIF_SIEGE",
                            "STATUTS_SIGNES", "FORMULAIRE_UNIQUE", "PV_NOMINATION_GERANT"));
                    if (dossier.isCapitalBlockageRequired()) types.add("ATTESTATION_BLOCAGE_CAPITAL");
                    yield types;
                }
                case "REGISTRE_COMMERCE" -> List.of("CIN", "JUSTIFICATIF_SIEGE",
                        "STATUTS_SIGNES", "FORMULAIRE_UNIQUE", "PV_NOMINATION_GERANT");
                default -> List.of("CIN");
            };
            for (String documentType : documentTypes) {
                if (documentRepository.existsByDossierIdAndDocumentTypeCode(dossier.getId(), documentType)) continue;
                createSeedDocument(documentRepository, dossier, documentType);
            }
        }
    }

    private void createSeedDocument(DocumentRepository documentRepository, Dossier dossier, String documentType) {
        String fileName = documentType.toLowerCase() + "_demo.pdf";
        String storedFileName = documentType + "_" + UUID.randomUUID() + ".pdf";
        Path targetDirectory = Paths.get(uploadBaseDir, "dossiers", dossier.getId().toString()).toAbsolutePath().normalize();
        Path targetFile = targetDirectory.resolve(storedFileName);
        try {
            Files.createDirectories(targetDirectory);
            try (PDDocument pdf = new PDDocument()) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                    content.newLineAtOffset(52, 740);
                    content.showText("FOUNDLY - DOCUMENT DE DEMONSTRATION");
                    content.newLineAtOffset(0, -28);
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    content.showText("Dossier : " + dossier.getReference());
                    content.newLineAtOffset(0, -18);
                    content.showText("Entreprise : " + dossier.getNomEntreprise());
                    content.newLineAtOffset(0, -18);
                    content.showText("Piece : " + documentType);
                    content.endText();
                }
                pdf.save(targetFile.toFile());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de créer le document de démonstration " + fileName, exception);
        }

        DocumentEntity document = new DocumentEntity();
        document.setDossier(dossier);
        document.setDocumentTypeCode(documentType);
        document.setOriginalFileName(fileName);
        document.setStoredFileName(storedFileName);
        document.setMimeType("application/pdf");
        document.setFileSize(targetFile.toFile().length());
        document.setSource(DocumentSource.UPLOADED);
        document.setFilePath(targetFile.toString());
        document.setUploadedBy(dossier.getEntrepreneur());
        documentRepository.save(document);
    }

    // =========================================================
    // SEED WORKFLOWS
    // =========================================================

    @Transactional
    public void seedWorkflowsIfMissing(
            WorkflowVersionRepository wfRepo,
            EtapeWorkflowRepository etapeRepo
    ) {
        // ---- AUTO-ENTREPRENEUR ----
        if (wfRepo.findByFormeJuridiqueAndStatut(LegalFormCode.AUTO_ENTREPRENEUR, WorkflowStatus.ACTIVE).isEmpty()) {
            WorkflowVersion wf = new WorkflowVersion();
            wf.setFormeJuridique(LegalFormCode.AUTO_ENTREPRENEUR);
            wf.setNumeroVersion("1.0");
            wf.setStatut(WorkflowStatus.ACTIVE);
            wf.setDescription("Circuit Auto-Entrepreneur - loi 114-13, sans capital ni certificat negatif");
            wf = wfRepo.save(wf);
            // RG3 : parcours allégé, sans certificat négatif, statuts, capital ni RC.
            seedEtape(etapeRepo, wf, 1, "VERIFICATION_ELIGIBILITE", "Vérification de l'éligibilité au statut Auto-Entrepreneur", "CRI", "CRI", BigDecimal.ZERO, "1 jour", null, true, false);
            seedEtape(etapeRepo, wf, 2, "FORMULAIRE_INSCRIPTION", "Dépôt du formulaire RNAE et justificatifs", null, "Entrepreneur", BigDecimal.ZERO, "1 jour", null, true, false);
            seedEtape(etapeRepo, wf, 3, "VALIDATION_POSTE_MAROC", "Validation d'identité et de conformité", "CRI", "CRI", BigDecimal.ZERO, "2 jours", null, true, false);
            seedEtape(etapeRepo, wf, 4, "DELIVRANCE_CARTE", "Attribution de l'identifiant et délivrance de la carte", "DGI", "DGI", BigDecimal.ZERO, "2 jours", null, true, false);
            log.info("Workflow AUTO_ENTREPRENEUR v1.0 cree");
        }

        // ---- SARL ----
        if (wfRepo.findByFormeJuridiqueAndStatut(LegalFormCode.SARL, WorkflowStatus.ACTIVE).isEmpty()) {
            WorkflowVersion wf = new WorkflowVersion();
            wf.setFormeJuridique(LegalFormCode.SARL);
            wf.setNumeroVersion("1.0");
            wf.setStatut(WorkflowStatus.ACTIVE);
            wf.setDescription("Circuit SARL - 8 etapes standard");
            wf = wfRepo.save(wf);
            seedSocietyWorkflow(etapeRepo, wf, "SARL dont capital > 100 000 MAD", false);
            log.info("Workflow SARL v1.0 cree");
        }

        // ---- SARL_AU ----
        if (wfRepo.findByFormeJuridiqueAndStatut(LegalFormCode.SARL_AU, WorkflowStatus.ACTIVE).isEmpty()) {
            WorkflowVersion wf = new WorkflowVersion();
            wf.setFormeJuridique(LegalFormCode.SARL_AU);
            wf.setNumeroVersion("1.0");
            wf.setStatut(WorkflowStatus.ACTIVE);
            wf.setDescription("Circuit SARL-AU (associe unique) - 8 etapes identiques a SARL");
            wf = wfRepo.save(wf);
            seedSocietyWorkflow(etapeRepo, wf, "SARL-AU dont capital > 100 000 MAD", false);
            log.info("Workflow SARL_AU v1.0 cree");
        }

        // ---- SA ----
        if (wfRepo.findByFormeJuridiqueAndStatut(LegalFormCode.SA, WorkflowStatus.ACTIVE).isEmpty()) {
            WorkflowVersion wf = new WorkflowVersion();
            wf.setFormeJuridique(LegalFormCode.SA);
            wf.setNumeroVersion("1.0");
            wf.setStatut(WorkflowStatus.ACTIVE);
            wf.setDescription("Circuit SA - 8 étapes standard, avec blocage du capital obligatoire");
            wf = wfRepo.save(wf);
            seedSocietyWorkflow(etapeRepo, wf, "SA : blocage du capital obligatoire", true);
            log.info("Workflow SA v1.0 cree");
        }
    }

    /** RG3/RG15 : même circuit de huit étapes pour les sociétés ; seule la condition de blocage varie. */
    private void seedSocietyWorkflow(
            EtapeWorkflowRepository repo,
            WorkflowVersion workflow,
            String capitalCondition,
            boolean capitalBlockageMandatory
    ) {
        seedEtape(repo, workflow, 1, "CERTIFICAT_NEGATIF", "Réservation du nom / certificat négatif", "OMPIC", "OMPIC", new BigDecimal("232.76"), "24-48 h", null, true, false);
        seedEtape(repo, workflow, 2, "REDACTION_STATUTS", "Rédaction, signature et légalisation des statuts", null, "Entrepreneur", BigDecimal.ZERO, "1 jour", null, true, false);
        seedEtape(repo, workflow, 3, "BLOCAGE_CAPITAL", "Blocage du capital social", null, "Banque partenaire externe", BigDecimal.ZERO, "1-2 jours", capitalCondition, capitalBlockageMandatory, true);
        seedEtape(repo, workflow, 4, "DEPOT_CRI", "Dépôt et contrôle au guichet unique", "CRI", "CRI", BigDecimal.ZERO, "2 jours", null, true, false);
        seedEtape(repo, workflow, 5, "REGISTRE_COMMERCE", "Immatriculation au Registre de Commerce", "TRIBUNAL", "Tribunal de commerce", BigDecimal.ZERO, "3 jours", null, true, false);
        seedEtape(repo, workflow, 6, "IDENTIFIANT_FISCAL", "Attribution de l'identifiant fiscal et de la taxe professionnelle", "DGI", "DGI", BigDecimal.ZERO, "2 jours", null, true, false);
        seedEtape(repo, workflow, 7, "AFFILIATION_CNSS", "Affiliation à la sécurité sociale", "CNSS", "CNSS", BigDecimal.ZERO, "1 jour", null, true, false);
        seedEtape(repo, workflow, 8, "PUBLICATION_LEGALE", "Publication légale au Journal et au Bulletin officiel", "JOURNAL", "Journal d'annonces légales / Bulletin officiel", BigDecimal.ZERO, "2 jours", null, true, false);
    }

    private void seedEtape(
            EtapeWorkflowRepository repo,
            WorkflowVersion wf,
            int ordre,
            String code,
            String libelle,
            String organismeCode,
            String organismeLibelle,
            BigDecimal frais,
            String delai,
            String condition,
            boolean obligatoire,
            boolean externe
    ) {
        EtapeWorkflow e = new EtapeWorkflow();
        e.setWorkflowVersion(wf);
        e.setOrdre(ordre);
        e.setCode(code);
        e.setLibelle(libelle);
        e.setOrganismeCode(organismeCode);
        e.setOrganismeLibelle(organismeLibelle);
        e.setFraisMad(frais);
        e.setDelaiIndicatif(delai);
        e.setConditionApplication(condition);
        e.setObligatoire(obligatoire);
        e.setExterne(externe);
        repo.save(e);
    }

    // =========================================================
    // HELPERS DOSSIERS & UTILISATEURS
    // =========================================================

    private void seedAgentIfMissing(
            UtilisateurRepository repo,
            PasswordEncoder encoder,
            String prenom,
            String nom,
            String email,
            String organisme,
            boolean actif,
            Instant lastActivity
    ) {
        if (!repo.existsByEmail(email)) {
            Utilisateur agent = new Utilisateur();
            agent.setPrenom(prenom);
            agent.setNom(nom);
            agent.setEmail(email);
            agent.setPassword(encoder.encode("Agent123456!"));
            agent.setRole(RoleUtilisateur.AGENT);
            agent.setOrganisme(organisme);
            agent.setActif(actif);
            agent.setCin("AG" + (1000 + (int)(Math.random() * 8000)));
            agent.setTelephone("06" + (10000000 + (int)(Math.random() * 80000000)));
            agent.setLastActivityAt(lastActivity);
            repo.save(agent);
        }
    }

    /**
     * Normalise les valeurs d'organisme pour tous les comptes AGENT.
     * Utilise en priorité l'email officiel pour corriger toute donnée anormale en base.
     */
    private void migrateAgentOrganismeToCode(UtilisateurRepository repo) {
        repo.findAll().stream()
                .filter(u -> u.getRole() == RoleUtilisateur.AGENT)
                .forEach(u -> {
                    String email = u.getEmail() != null ? u.getEmail().toLowerCase() : "";
                    String org = u.getOrganisme();
                    String newCode = null;

                    if (email.contains("ompic")) newCode = "OMPIC";
                    else if (email.contains("cri")) newCode = "CRI";
                    else if (email.contains("tribunal") || email.contains("justice")) newCode = "TRIBUNAL";
                    else if (email.contains("dgi") || email.contains("impot")) newCode = "DGI";
                    else if (email.contains("cnss")) newCode = "CNSS";
                    else if (email.contains("journal") || email.contains("annonce")) newCode = "JOURNAL";

                    if (newCode == null && org != null) {
                        String upper = org.toUpperCase().trim();
                        if (upper.contains("CRI") || upper.contains("INVESTISSEMENT")) newCode = "CRI";
                        else if (upper.contains("TRIBUNAL") || upper.contains("COMMERCE") || upper.contains("JUSTICE")) newCode = "TRIBUNAL";
                        else if (upper.contains("CNSS") || upper.contains("SECURITE")) newCode = "CNSS";
                        else if (upper.contains("OMPIC") || upper.contains("PROPRIET") || upper.contains("INDUSTRIEL")) newCode = "OMPIC";
                        else if (upper.contains("DGI") || upper.contains("IMPOT") || upper.contains("FISC")) newCode = "DGI";
                        else if (upper.contains("JOURNAL") || upper.contains("ANNONCE")) newCode = "JOURNAL";
                    }

                    if (newCode != null && !newCode.equalsIgnoreCase(org)) {
                        u.setOrganisme(newCode);
                        repo.save(u);
                        log.info("Organisme de l'agent {} ({}) migre vers {}", u.getEmail(), org, newCode);
                    }
                });
    }

    private Utilisateur seedEntrepreneur(
            UtilisateurRepository repo,
            PasswordEncoder encoder,
            String prenom,
            String nom,
            String email,
            String cin,
            String telephone
    ) {
        return repo.findByEmail(email).orElseGet(() -> {
            Utilisateur u = new Utilisateur();
            u.setPrenom(prenom);
            u.setNom(nom);
            u.setEmail(email);
            u.setPassword(encoder.encode("Pass123456!"));
            u.setRole(RoleUtilisateur.ENTREPRENEUR);
            u.setActif(true);
            u.setCin(cin);
            u.setTelephone(telephone);
            u.setLastActivityAt(Instant.now());
            return repo.save(u);
        });
    }

    /**
     * Creates a dossier with its workflow steps DIRECTLY (no WorkflowEngineService call),
     * avoiding transactional deadlocks during startup seeding.
     */
    private void seedDossier(
            DossierRepository repo,
            SuiviEtapeRepository stepRepo,
            String ref,
            String nomEntreprise,
            LegalFormCode forme,
            DossierStatus statut,
            String region,
            String etape,
            BigDecimal capital,
            Utilisateur ent,
            int daysAgo,
            String motifRejet
    ) {
        Dossier d = new Dossier();
        d.setReference(ref);
        d.setNomEntreprise(nomEntreprise);
        d.setFormeJuridique(forme);
        d.setStatut(statut);
        d.setRegion(region);
        d.setEtapeActuelle(etape);
        d.setCapitalMad(capital);
        boolean blockage = (forme == LegalFormCode.SA) || (forme != LegalFormCode.AUTO_ENTREPRENEUR && capital.compareTo(new BigDecimal("100000")) > 0);
        d.setCapitalBlockageRequired(blockage);
        d.setRejectionReason(motifRejet);
        d.setEntrepreneur(ent);
        d.setCreatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        d.setUpdatedAt(Instant.now().minus(Math.max(1, daysAgo - 4), ChronoUnit.DAYS));
        Dossier saved = repo.save(d);

        // Create workflow steps DIRECTLY
        List<SuiviEtape> steps = buildStepsForDossier(saved);
        steps = stepRepo.saveAll(steps);

        // Set initial step tracking on dossier
        if (!steps.isEmpty()) {
            SuiviEtape first = steps.get(0);
            saved.setCurrentStepCode(first.getEtapeCode());
            saved.setCurrentStepOrder(first.getOrdre());
            saved.setEtapeActuelle(first.getLibelle());
        }

        // Apply status-specific modifications
        if (statut == DossierStatus.FINALISE) {
            for (SuiviEtape s : steps) {
                s.setStatut(StepStatus.VALIDEE);
                s.setDecidedAt(Instant.now().minus(Math.max(1, daysAgo - 5), ChronoUnit.DAYS));
            }
            stepRepo.saveAll(steps);
            saved.setFinalizedAt(Instant.now().minus(Math.max(1, daysAgo - 5), ChronoUnit.DAYS));
            saved.setEtapeActuelle("Procedure finalisee - Entreprise immatriculee");
            repo.save(saved);
        } else if (statut == DossierStatus.A_CORRIGER) {
            for (int i = 0; i < steps.size(); i++) {
                SuiviEtape s = steps.get(i);
                if (i < 3) {
                    s.setStatut(StepStatus.VALIDEE);
                    s.setDecidedAt(Instant.now().minus(Math.max(2, daysAgo - 2), ChronoUnit.DAYS));
                } else if (i == 3) {
                    s.setStatut(StepStatus.REJETEE);
                    s.setMotifRejet(motifRejet);
                    s.setDecidedAt(Instant.now().minus(1, ChronoUnit.DAYS));
                    saved.setCurrentStepCode(s.getEtapeCode());
                    saved.setCurrentStepOrder(s.getOrdre());
                    saved.setEtapeActuelle(s.getLibelle());
                }
            }
            stepRepo.saveAll(steps);
            repo.save(saved);
        } else {
            repo.save(saved);
        }
    }

    private List<SuiviEtape> buildStepsForDossier(Dossier dossier) {
        List<SuiviEtape> steps = new ArrayList<>();

        if (dossier.getFormeJuridique() == LegalFormCode.AUTO_ENTREPRENEUR) {
            steps.add(createStep(dossier, "VERIFICATION_ELIGIBILITE", "Verification de l'eligibilite au statut Auto-Entrepreneur", 1, ActorType.AGENT, "CRI", BigDecimal.ZERO, false, 1));
            steps.add(createStep(dossier, "FORMULAIRE_INSCRIPTION", "Depot du formulaire RNAE et justificatifs", 2, ActorType.ENTREPRENEUR, null, BigDecimal.ZERO, false, 1));
            steps.add(createStep(dossier, "VALIDATION_POSTE_MAROC", "Validation d'identite en agence et conformite", 3, ActorType.AGENT, "CRI", BigDecimal.ZERO, false, 2));
            steps.add(createStep(dossier, "DELIVRANCE_CARTE", "Attribution identifiant fiscal et delivrance de la carte", 4, ActorType.AGENT, "DGI", BigDecimal.ZERO, false, 2));
        } else {
            steps.add(createStep(dossier, "CERTIFICAT_NEGATIF", "Reservation du nom / Certificat negatif (OMPIC)", 1, ActorType.AGENT, "OMPIC", new BigDecimal("232.76"), true, 2));
            steps.add(createStep(dossier, "REDACTION_STATUTS", "Redaction, signature et legalisation des statuts", 2, ActorType.ENTREPRENEUR, "CRI", BigDecimal.ZERO, false, 1));

            SuiviEtape step3 = createStep(dossier, "BLOCAGE_CAPITAL", "Blocage du capital social (Banque externe)", 3, ActorType.ENTREPRENEUR, null, BigDecimal.ZERO, false, 2);
            if (!dossier.isCapitalBlockageRequired()) {
                step3.setStatut(StepStatus.VALIDEE);
                step3.setDecidedAt(Instant.now());
            }
            steps.add(step3);

            steps.add(createStep(dossier, "DEPOT_CRI", "Depot et controle au guichet unique (CRI)", 4, ActorType.AGENT, "CRI", BigDecimal.ZERO, false, 2));
            steps.add(createStep(dossier, "REGISTRE_COMMERCE", "Immatriculation au Registre de Commerce (Tribunal)", 5, ActorType.AGENT, "TRIBUNAL", BigDecimal.ZERO, false, 3));
            steps.add(createStep(dossier, "IDENTIFIANT_FISCAL", "Attribution Identifiant Fiscal & Taxe Professionnelle (DGI)", 6, ActorType.AGENT, "DGI", BigDecimal.ZERO, false, 2));
            steps.add(createStep(dossier, "AFFILIATION_CNSS", "Affiliation a la securite sociale (CNSS)", 7, ActorType.AGENT, "CNSS", BigDecimal.ZERO, false, 1));
            steps.add(createStep(dossier, "PUBLICATION_LEGALE", "Publication legale au Journal d'Annonces Legales & BO", 8, ActorType.AGENT, "JOURNAL", BigDecimal.ZERO, false, 2));
        }

        // Premiere etape active
        SuiviEtape firstStep = steps.get(0);
        firstStep.setStatut(StepStatus.EN_COURS);
        firstStep.setStartedAt(Instant.now());

        return steps;
    }

    private SuiviEtape createStep(
            Dossier dossier,
            String code,
            String libelle,
            int ordre,
            ActorType actionOwner,
            String organismeCode,
            BigDecimal fraisMad,
            boolean paymentRequired,
            int durationDays
    ) {
        SuiviEtape s = new SuiviEtape();
        s.setDossier(dossier);
        s.setEtapeCode(code);
        s.setLibelle(libelle);
        s.setOrdre(ordre);
        s.setActionOwner(actionOwner);
        s.setOrganismeCode(organismeCode);
        s.setStatut(StepStatus.EN_ATTENTE);
        s.setFraisMad(fraisMad);
        s.setPaymentRequired(paymentRequired);
        s.setIndicativeDurationDays(durationDays);
        return s;
    }

    private void seedOrganismeIfMissing(
            OrganismeRepository repo,
            String code,
            String nom,
            String description
    ) {
        if (!repo.existsByCode(code)) {
            Organisme o = new Organisme();
            o.setCode(code);
            o.setNom(nom);
            o.setDescription(description);
            repo.save(o);
        }
    }
}
