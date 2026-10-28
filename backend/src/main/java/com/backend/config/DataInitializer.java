package com.backend.config;

import com.backend.entity.Dossier;
import com.backend.entity.EtapeWorkflow;
import com.backend.entity.Organisme;
import com.backend.entity.SuiviEtape;
import com.backend.entity.Utilisateur;
import com.backend.entity.WorkflowVersion;
import com.backend.enums.ActorType;
import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;
import com.backend.enums.RoleUtilisateur;
import com.backend.enums.StepStatus;
import com.backend.enums.WorkflowStatus;
import com.backend.repository.DossierRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initDatabase(
            UtilisateurRepository utilisateurRepository,
            DossierRepository dossierRepository,
            OrganismeRepository organismeRepository,
            SuiviEtapeRepository suiviEtapeRepository,
            PasswordEncoder passwordEncoder,
            WorkflowVersionRepository workflowVersionRepository,
            EtapeWorkflowRepository etapeWorkflowRepository
    ) {
        return args -> runInit(
                utilisateurRepository, dossierRepository, organismeRepository,
                suiviEtapeRepository, passwordEncoder,
                workflowVersionRepository, etapeWorkflowRepository
        );
    }

    @Transactional
    public void runInit(
            UtilisateurRepository utilisateurRepository,
            DossierRepository dossierRepository,
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

        // Migration : corriger les agents existants dont le champ organisme n'est pas encore un code
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
            log.info("=== Foundly DataInitializer: dossiers deja presents ({}) — seed ignore ===", dossierRepository.count());
        }

        // 4. Seed workflows par defaut (idempotent)
        // RG14 : ces versions sont creees ACTIVE une seule fois ;
        // les dossiers en cours conservent leur workflow initial.
        seedWorkflowsIfMissing(workflowVersionRepository, etapeWorkflowRepository);

        log.info("=== Foundly DataInitializer: termine avec succes ===");
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
            seedEtape(etapeRepo, wf, 1, "CREATION_COMPTE", "Creation de compte (rn.ae.gov.ma)", null, "Systeme", BigDecimal.ZERO, "Immediat", null, true, false);
            seedEtape(etapeRepo, wf, 2, "FORMULAIRE_INSCRIPTION", "Formulaire d'inscription (etat civil, activite)", null, "Systeme", BigDecimal.ZERO, "15 min", null, true, false);
            seedEtape(etapeRepo, wf, 3, "DEPOT_DOSSIER", "Depot du dossier signe", "BARID", "Barid Al-Maghrib", BigDecimal.ZERO, "Variable", null, true, false);
            seedEtape(etapeRepo, wf, 4, "EXAMEN_DOSSIER", "Examen du dossier", "RNAE", "Registre National Auto-Entrepreneur", BigDecimal.ZERO, "48-72h", null, true, false);
            seedEtape(etapeRepo, wf, 5, "ATTRIBUTION_NUMERO", "Attribution numero RNAE/ICE + carte", "RNAE", "Registre National Auto-Entrepreneur", BigDecimal.ZERO, "48-72h", null, true, false);
            seedEtape(etapeRepo, wf, 6, "AFFILIATION_CNSS", "Affiliation CNSS/AMO", "CNSS", "CNSS", BigDecimal.ZERO, "1-2 jours", null, true, false);
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
            seedEtape(etapeRepo, wf, 1, "CERTIFICAT_NEGATIF", "Certificat negatif", "OMPIC", "OMPIC", new BigDecimal("230"), "24-48h", null, true, false);
            seedEtape(etapeRepo, wf, 2, "PREPARATION_STATUTS", "Preparation des statuts", null, "Foundly/Systeme", BigDecimal.ZERO, "1 jour", null, true, false);
            seedEtape(etapeRepo, wf, 3, "BLOCAGE_CAPITAL", "Blocage du capital en banque", null, "Banque partenaire externe", BigDecimal.ZERO, "1-2 jours", "SARL dont capital > 100 000 MAD", false, true);
            seedEtape(etapeRepo, wf, 4, "DEPOT_CRI", "Depot et controle CRI", "CRI", "CRI", new BigDecimal("1000"), "2-4 jours", null, true, false);
            seedEtape(etapeRepo, wf, 5, "IMMATRICULATION_RC", "Immatriculation au Registre de commerce", "TRIBUNAL", "Tribunal de commerce", new BigDecimal("350"), "1-2 jours", null, true, false);
            seedEtape(etapeRepo, wf, 6, "IDENTIFIANT_FISCAL", "Identifiant fiscal et taxe professionnelle", "DGI", "DGI", BigDecimal.ZERO, "2-3 jours", null, true, false);
            seedEtape(etapeRepo, wf, 7, "AFFILIATION_CNSS_SARL", "Affiliation CNSS", "CNSS", "CNSS", BigDecimal.ZERO, "1-2 jours", "Si salaries declares", false, false);
            seedEtape(etapeRepo, wf, 8, "PUBLICATION_LEGALE", "Publication legale", "JOURNAL", "Journal d'annonces legales / Bulletin Officiel", BigDecimal.ZERO, "2-5 jours", null, true, false);
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
            seedEtape(etapeRepo, wf, 1, "CERTIFICAT_NEGATIF_SAU", "Certificat negatif", "OMPIC", "OMPIC", new BigDecimal("230"), "24-48h", null, true, false);
            seedEtape(etapeRepo, wf, 2, "PREPARATION_STATUTS_SAU", "Preparation des statuts", null, "Foundly/Systeme", BigDecimal.ZERO, "1 jour", null, true, false);
            seedEtape(etapeRepo, wf, 3, "BLOCAGE_CAPITAL_SAU", "Blocage du capital en banque", null, "Banque partenaire externe", BigDecimal.ZERO, "1-2 jours", "SARL-AU dont capital > 100 000 MAD", false, true);
            seedEtape(etapeRepo, wf, 4, "DEPOT_CRI_SAU", "Depot et controle CRI", "CRI", "CRI", new BigDecimal("1000"), "2-4 jours", null, true, false);
            seedEtape(etapeRepo, wf, 5, "IMMATRICULATION_RC_SAU", "Immatriculation au Registre de commerce", "TRIBUNAL", "Tribunal de commerce", new BigDecimal("350"), "1-2 jours", null, true, false);
            seedEtape(etapeRepo, wf, 6, "IDENTIFIANT_FISCAL_SAU", "Identifiant fiscal et taxe professionnelle", "DGI", "DGI", BigDecimal.ZERO, "2-3 jours", null, true, false);
            seedEtape(etapeRepo, wf, 7, "AFFILIATION_CNSS_SAU", "Affiliation CNSS", "CNSS", "CNSS", BigDecimal.ZERO, "1-2 jours", "Si salaries declares", false, false);
            seedEtape(etapeRepo, wf, 8, "PUBLICATION_LEGALE_SAU", "Publication legale", "JOURNAL", "Journal d'annonces legales / Bulletin Officiel", BigDecimal.ZERO, "2-5 jours", null, true, false);
            log.info("Workflow SARL_AU v1.0 cree");
        }

        // ---- SA ----
        if (wfRepo.findByFormeJuridiqueAndStatut(LegalFormCode.SA, WorkflowStatus.ACTIVE).isEmpty()) {
            WorkflowVersion wf = new WorkflowVersion();
            wf.setFormeJuridique(LegalFormCode.SA);
            wf.setNumeroVersion("1.0");
            wf.setStatut(WorkflowStatus.ACTIVE);
            wf.setDescription("Circuit SA - 10 etapes, capital min. 300 000 MAD");
            wf = wfRepo.save(wf);
            seedEtape(etapeRepo, wf, 1, "CERTIFICAT_NEGATIF_SA", "Certificat negatif", "OMPIC", "OMPIC", new BigDecimal("230"), "24-48h", null, true, false);
            seedEtape(etapeRepo, wf, 2, "REDACTION_STATUTS_SA", "Redaction des statuts SA", null, "Foundly/Systeme", BigDecimal.ZERO, "Variable", null, true, false);
            seedEtape(etapeRepo, wf, 3, "BULLETINS_SOUSCRIPTION", "Bulletins de souscription, PV CA et designation commissaire aux comptes", null, "Foundly/Systeme", BigDecimal.ZERO, "Variable", null, true, false);
            seedEtape(etapeRepo, wf, 4, "BLOCAGE_CAPITAL_SA", "Blocage du capital (min. 25% de 300 000 MAD)", null, "Banque partenaire externe", BigDecimal.ZERO, "1-2 jours", "SA - capital minimum 300 000 MAD requis", true, true);
            seedEtape(etapeRepo, wf, 5, "DEPOT_CRI_SA", "Depot et controle CRI", "CRI", "CRI", new BigDecimal("1000"), "2-4 jours", null, true, false);
            seedEtape(etapeRepo, wf, 6, "ENREGISTREMENT_ACTES_SA", "Enregistrement des actes", "DGI", "DGI", BigDecimal.ZERO, "2-3 jours", null, true, false);
            seedEtape(etapeRepo, wf, 7, "IMMATRICULATION_RC_SA", "Immatriculation au Registre de commerce", "TRIBUNAL", "Tribunal de commerce", new BigDecimal("350"), "3-5 jours", null, true, false);
            seedEtape(etapeRepo, wf, 8, "IDENTIFIANT_FISCAL_SA", "Identifiant fiscal et taxe professionnelle", "DGI", "DGI", BigDecimal.ZERO, "2-3 jours", null, true, false);
            seedEtape(etapeRepo, wf, 9, "AFFILIATION_CNSS_SA", "Affiliation CNSS", "CNSS", "CNSS", BigDecimal.ZERO, "1-2 jours", null, true, false);
            seedEtape(etapeRepo, wf, 10, "PUBLICATION_LEGALE_SA", "Publication legale (journal + Bulletin Officiel)", "JOURNAL", "Journal / Bulletin Officiel", BigDecimal.ZERO, "2-5 jours", null, true, false);
            log.info("Workflow SA v1.0 cree");
        }
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
     * Corrige les valeurs d'organisme stockees en BDD qui utilisaient un nom libre
     * au lieu du code d'organisme attendu par la requete de comptage activeAgentCount.
     */
    private void migrateAgentOrganismeToCode(UtilisateurRepository repo) {
        java.util.Map<String, String> mapping = new java.util.LinkedHashMap<>();
        mapping.put("CRI Casablanca-Settat", "CRI");
        mapping.put("Tribunal de commerce",  "TRIBUNAL");
        mapping.put("Tribunaux de commerce", "TRIBUNAL");
        mapping.put("Caisse Nationale de Securite Sociale", "CNSS");
        mapping.put("Office Marocain de la Propriete Industrielle et Commerciale", "OMPIC");
        mapping.put("Direction Generale des Impots", "DGI");

        mapping.forEach((oldValue, newCode) ->
                repo.findAll().stream()
                        .filter(u -> u.getRole() == RoleUtilisateur.AGENT
                                && oldValue.equals(u.getOrganisme()))
                        .forEach(u -> {
                            u.setOrganisme(newCode);
                            repo.save(u);
                        })
        );
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

        // Create workflow steps DIRECTLY (not via WorkflowEngineService to avoid deadlocks)
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

    /**
     * Builds workflow steps for a dossier (mirrors WorkflowEngineService logic without @Transactional).
     */
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
