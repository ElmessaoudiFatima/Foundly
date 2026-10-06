package com.backend.config;

import com.backend.entity.Dossier;
import com.backend.entity.Utilisateur;
import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;
import com.backend.enums.RoleUtilisateur;
import com.backend.repository.DossierRepository;
import com.backend.repository.UtilisateurRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initDatabase(
            UtilisateurRepository utilisateurRepository,
            DossierRepository dossierRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
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

            // 2. Agents des organismes
            seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                    "Khadija", "Berrada", "khadija.berrada@cri.ma", "CRI Casablanca-Settat", true, Instant.now().minus(30, ChronoUnit.MINUTES));

            seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                    "Nadia", "El Fassi", "nadia.elfassi@ompic.ma", "OMPIC", true, Instant.now().minus(2, ChronoUnit.HOURS));

            seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                    "Youssef", "Karim", "y.karim@tribunal.ma", "Tribunal de commerce", true, Instant.now().minus(1, ChronoUnit.DAYS));

            seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                    "Leïla", "Amrani", "l.amrani@dgi.gov.ma", "DGI", false, Instant.now().minus(15, ChronoUnit.DAYS));

            seedAgentIfMissing(utilisateurRepository, passwordEncoder,
                    "Karim", "Bennis", "k.bennis@cnss.ma", "CNSS", true, Instant.now().minus(3, ChronoUnit.HOURS));

            // 3. Entrepreneurs et dossiers initiaux
            if (dossierRepository.count() == 0) {
                Utilisateur ent1 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Aya", "El Mansouri", "aya.mansouri@gmail.com", "BK789456", "0612345678");
                Utilisateur ent2 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Salma", "Idrissi", "salma.idrissi@gmail.com", "AB654321", "0623456789");
                Utilisateur ent3 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Youssef", "Amrani", "youssef.amrani@gmail.com", "C987654", "0634567890");
                Utilisateur ent4 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Imane", "Dennis", "imane.dennis@gmail.com", "EE123456", "0645678901");
                Utilisateur ent5 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Omar", "Alaoui", "omar.alaoui@gmail.com", "F543210", "0656789012");
                Utilisateur ent6 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Mehdi", "Tazi", "mehdi.tazi@gmail.com", "CD112233", "0677889900");
                Utilisateur ent7 = seedEntrepreneur(utilisateurRepository, passwordEncoder, "Kenza", "Bennani", "kenza.bennani@gmail.com", "G445566", "0699001122");

                seedDossier(dossierRepository, "FND-2025-04821", "Atlas Studio", LegalFormCode.SARL,
                        DossierStatus.EN_COURS, "Casablanca-Settat", "Contrôle CRI", new BigDecimal("100000"), ent1, 8);

                seedDossier(dossierRepository, "FND-2025-04836", "Maison Zayna", LegalFormCode.SARL_AU,
                        DossierStatus.EN_COURS, "Rabat-Salé-Kénitra", "Registre de commerce", new BigDecimal("50000"), ent2, 6);

                seedDossier(dossierRepository, "FND-2025-04812", "Nexa Conseil", LegalFormCode.SARL,
                        DossierStatus.A_CORRIGER, "Casablanca-Settat", "Contrôle CRI", new BigDecimal("150000"), ent3, 10);

                seedDossier(dossierRepository, "FND-2025-04798", "Terres d'Atlas", LegalFormCode.SA,
                        DossierStatus.FINALISE, "Marrakech-Safi", "Publication légale", new BigDecimal("350000"), ent4, 16);

                seedDossier(dossierRepository, "FND-2025-04781", "Riad Digital", LegalFormCode.AUTO_ENTREPRENEUR,
                        DossierStatus.FINALISE, "Fès-Meknès", "Immatriculation", BigDecimal.ZERO, ent5, 20);

                seedDossier(dossierRepository, "FND-2025-04850", "MedTech Solutions", LegalFormCode.SARL_AU,
                        DossierStatus.SOUMIS, "Tanger-Tétouan-Al Hoceïma", "Certificat négatif", new BigDecimal("80000"), ent6, 2);

                seedDossier(dossierRepository, "FND-2025-04862", "EcoTransport Maghreb", LegalFormCode.SARL,
                        DossierStatus.BROUILLON, "Casablanca-Settat", "Constitution dossier", new BigDecimal("200000"), ent7, 1);
            }
        };
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

    private void seedDossier(
            DossierRepository repo,
            String ref,
            String nomEntreprise,
            LegalFormCode forme,
            DossierStatus statut,
            String region,
            String etape,
            BigDecimal capital,
            Utilisateur ent,
            int daysAgo
    ) {
        Dossier d = new Dossier();
        d.setReference(ref);
        d.setNomEntreprise(nomEntreprise);
        d.setFormeJuridique(forme);
        d.setStatut(statut);
        d.setRegion(region);
        d.setEtapeActuelle(etape);
        d.setCapitalMad(capital);
        d.setEntrepreneur(ent);
        d.setCreatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        d.setUpdatedAt(Instant.now().minus(Math.max(1, daysAgo - 4), ChronoUnit.DAYS));
        repo.save(d);
    }
}
