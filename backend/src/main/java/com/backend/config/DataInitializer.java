package com.backend.config;

import com.backend.entity.Dossier;
import com.backend.entity.Organisme;
import com.backend.entity.SuiviEtape;
import com.backend.entity.Utilisateur;
import com.backend.enums.ActorType;
import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;
import com.backend.enums.RoleUtilisateur;
import com.backend.enums.StepStatus;
import com.backend.repository.DossierRepository;
import com.backend.repository.OrganismeRepository;
import com.backend.repository.SuiviEtapeRepository;
import com.backend.repository.UtilisateurRepository;
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
            PasswordEncoder passwordEncoder
    ) {
        return args -> runInit(utilisateurRepository, dossierRepository, organismeRepository, suiviEtapeRepository, passwordEncoder);
    }

    @Transactional
    public void runInit(
            UtilisateurRepository utilisateurRepository,
            DossierRepository dossierRepository,
            OrganismeRepository organismeRepository,
            SuiviEtapeRepository suiviEtapeRepository,
            PasswordEncoder passwordEncoder
    ) {
        log.info("=== Foundly DataInitializer: début du seed ===");

        // 0. Seed organismes partenaires (idempotent : insertion si code absent)
        seedOrganismeIfMissing(organismeRepository, "OMPIC",
                "Office Marocain de la Propriété Industrielle et Commerciale",
                "Délivre le certificat négatif attestant la disponibilité du nom commercial.");
        seedOrganismeIfMissing(organismeRepository, "CRI",
                "Centres Régionaux d'Investissement",
                "Guichet unique régional qui réceptionne et traite les dossiers de création d'entreprise.");
        seedOrganismeIfMissing(organismeRepository, "TRIBUNAL",
                "Tribunaux de commerce",
                "Prononce l'immatriculation de la société au Registre du Commerce et des Sociétés.");
        seedOrganismeIfMissing(organismeRepository, "DGI",
                "Direction Générale des Impôts",
                "Attribue l'Identifiant Fiscal (IF) et la patente nécessaires à l'exercice de l'activité.");
        seedOrganismeIfMissing(organismeRepository, "CNSS",
                "Caisse Nationale de Sécurité Sociale",
                "Enregistre l'entreprise comme employeur pour la couverture sociale des salariés.");
        seedOrganismeIfMissing(organismeRepository, "JOURNAL",
                "Journaux d'annonces légales",
                "Publie l'avis de constitution de la société conformément aux obligations légales.");

        // 1. Administrateur principal (Samira Benjelloun - Superviseure Foundly)
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

        // 2. Agents des organismes (organisme = code pour matcher la table Organisme)
        seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                "Khadija", "Berrada", "khadija.berrada@cri.ma", "CRI", true, Instant.now().minus(30, ChronoUnit.MINUTES));

        seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                "Nadia", "El Fassi", "nadia.elfassi@ompic.ma", "OMPIC", true, Instant.now().minus(2, ChronoUnit.HOURS));

        seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                "Youssef", "Karim", "y.karim@tribunal.ma", "TRIBUNAL", true, Instant.now().minus(1, ChronoUnit.DAYS));

        seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                "Leïla", "Amrani", "l.amrani@dgi.gov.ma", "DGI", false, Instant.now().minus(15, ChronoUnit.DAYS));

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
                    DossierStatus.EN_COURS, "Casablanca-Settat", "Contrôle CRI", new BigDecimal("100000"), ent1, 8, null);

            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04836", "Maison Zayna", LegalFormCode.SARL_AU,
                    DossierStatus.EN_COURS, "Rabat-Salé-Kénitra", "Registre de commerce", new BigDecimal("50000"), ent2, 6, null);

            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04812", "Nexa Conseil", LegalFormCode.SARL,
                    DossierStatus.A_CORRIGER, "Casablanca-Settat", "Contrôle CRI", new BigDecimal("150000"), ent3, 10,
                    "Attestation de blocage du capital non conforme : le montant libellé ne correspond pas au capital statutaire de 150 000 MAD.");

            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04798", "Terres d'Atlas", LegalFormCode.SA,
                    DossierStatus.FINALISE, "Marrakech-Safi", "Publication légale", new BigDecimal("350000"), ent4, 16, null);

            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04781", "Riad Digital", LegalFormCode.AUTO_ENTREPRENEUR,
                    DossierStatus.FINALISE, "Fès-Meknès", "Immatriculation", BigDecimal.ZERO, ent5, 20, null);

            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04850", "MedTech Solutions", LegalFormCode.SARL_AU,
                    DossierStatus.SOUMIS, "Tanger-Tétouan-Al Hoceïma", "Certificat négatif", new BigDecimal("80000"), ent6, 2, null);

            seedDossier(dossierRepository, suiviEtapeRepository, "FND-2025-04862", "EcoTransport Maghreb", LegalFormCode.SARL,
                    DossierStatus.BROUILLON, "Casablanca-Settat", "Constitution dossier", new BigDecimal("200000"), ent7, 1, null);

            log.info("=== Foundly DataInitializer: {} dossiers seedés avec succès ===", dossierRepository.count());
        } else {
            log.info("=== Foundly DataInitializer: dossiers déjà présents ({}) — seed ignoré ===", dossierRepository.count());
        }

        log.info("=== Foundly DataInitializer: terminé avec succès ===");
    }

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
     * Corrige les valeurs d'organisme stockées en BDD qui utilisaient un nom libre
     * au lieu du code d'organisme attendu par la requête de comptage activeAgentCount.
     */
    private void migrateAgentOrganismeToCode(UtilisateurRepository repo) {
        java.util.Map<String, String> mapping = new java.util.LinkedHashMap<>();
        mapping.put("CRI Casablanca-Settat", "CRI");
        mapping.put("Tribunal de commerce",  "TRIBUNAL");
        mapping.put("Tribunaux de commerce", "TRIBUNAL");
        mapping.put("Caisse Nationale de Sécurité Sociale", "CNSS");
        mapping.put("Office Marocain de la Propriété Industrielle et Commerciale", "OMPIC");
        mapping.put("Direction Générale des Impôts", "DGI");

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

        // Create workflow steps DIRECTLY (not via WorkflowEngineService to avoid @Transactional deadlocks)
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
            saved.setEtapeActuelle("Procédure finalisée — Entreprise immatriculée");
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
            steps.add(createStep(dossier, "VERIFICATION_ELIGIBILITE", "Vérification de l'éligibilité au statut Auto-Entrepreneur", 1, ActorType.AGENT, "CRI", BigDecimal.ZERO, false, 1));
            steps.add(createStep(dossier, "FORMULAIRE_INSCRIPTION", "Dépôt du formulaire RNAE et justificatifs", 2, ActorType.ENTREPRENEUR, null, BigDecimal.ZERO, false, 1));
            steps.add(createStep(dossier, "VALIDATION_POSTE_MAROC", "Validation d'identité en agence et conformité", 3, ActorType.AGENT, "CRI", BigDecimal.ZERO, false, 2));
            steps.add(createStep(dossier, "DELIVRANCE_CARTE", "Attribution identifiant fiscal et délivrance de la carte", 4, ActorType.AGENT, "DGI", BigDecimal.ZERO, false, 2));
        } else {
            steps.add(createStep(dossier, "CERTIFICAT_NEGATIF", "Réservation du nom / Certificat négatif (OMPIC)", 1, ActorType.AGENT, "OMPIC", new BigDecimal("232.76"), true, 2));
            steps.add(createStep(dossier, "REDACTION_STATUTS", "Rédaction, signature et légalisation des statuts", 2, ActorType.ENTREPRENEUR, "CRI", BigDecimal.ZERO, false, 1));

            SuiviEtape step3 = createStep(dossier, "BLOCAGE_CAPITAL", "Blocage du capital social (Banque externe)", 3, ActorType.ENTREPRENEUR, null, BigDecimal.ZERO, false, 2);
            if (!dossier.isCapitalBlockageRequired()) {
                step3.setStatut(StepStatus.VALIDEE);
                step3.setDecidedAt(Instant.now());
            }
            steps.add(step3);

            steps.add(createStep(dossier, "DEPOT_CRI", "Dépôt et contrôle au guichet unique (CRI)", 4, ActorType.AGENT, "CRI", BigDecimal.ZERO, false, 2));
            steps.add(createStep(dossier, "REGISTRE_COMMERCE", "Immatriculation au Registre de Commerce (Tribunal)", 5, ActorType.AGENT, "TRIBUNAL", BigDecimal.ZERO, false, 3));
            steps.add(createStep(dossier, "IDENTIFIANT_FISCAL", "Attribution Identifiant Fiscal & Taxe Professionnelle (DGI)", 6, ActorType.AGENT, "DGI", BigDecimal.ZERO, false, 2));
            steps.add(createStep(dossier, "AFFILIATION_CNSS", "Affiliation à la sécurité sociale (CNSS)", 7, ActorType.AGENT, "CNSS", BigDecimal.ZERO, false, 1));
            steps.add(createStep(dossier, "PUBLICATION_LEGALE", "Publication légale au Journal d'Annonces Légales & BO", 8, ActorType.AGENT, "JOURNAL", BigDecimal.ZERO, false, 2));
        }

        // Première étape active
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
