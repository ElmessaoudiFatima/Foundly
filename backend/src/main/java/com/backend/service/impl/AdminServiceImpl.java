package com.backend.service.impl;

import com.backend.dto.admin.*;
import com.backend.entity.Dossier;
import com.backend.entity.Utilisateur;
import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;
import com.backend.enums.RoleUtilisateur;
import com.backend.exception.EmailAlreadyUsedException;
import com.backend.repository.DossierRepository;
import com.backend.repository.UtilisateurRepository;
import com.backend.service.AdminService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminServiceImpl implements AdminService {

    private final DossierRepository dossierRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
            .withZone(ZoneId.of("Africa/Casablanca"));

    private static final DateTimeFormatter FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH)
            .withZone(ZoneId.of("Africa/Casablanca"));

    public AdminServiceImpl(
            DossierRepository dossierRepository,
            UtilisateurRepository utilisateurRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.dossierRepository = dossierRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardStats(String period, String region) {
        String regionFilter = normalizeRegion(region);
        ZoneId zone = ZoneId.of("Africa/Casablanca");
        int chartMonths = resolveChartMonths(period);
        Instant periodStart = resolvePeriodStart(period, zone);

        long totalCount = dossierRepository.count(dossierSpec(regionFilter, null, periodStart));
        long enCoursCount = dossierRepository.count(dossierSpec(regionFilter, DossierStatus.EN_COURS, periodStart));
        long finalisesCount = dossierRepository.count(dossierSpec(regionFilter, DossierStatus.FINALISE, periodStart));
        long aCorrigerCount = dossierRepository.count(dossierSpec(regionFilter, DossierStatus.A_CORRIGER, periodStart));

        double rejectionRate = 0.0;
        if (totalCount > 0) {
            rejectionRate = Math.round(((double) aCorrigerCount / totalCount) * 1000.0) / 10.0;
        }

        double enCoursPct = totalCount > 0
                ? Math.round(((double) enCoursCount / totalCount) * 1000.0) / 10.0
                : 0.0;

        Instant now = Instant.now();
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
        Instant sixtyDaysAgo = now.minus(60, ChronoUnit.DAYS);

        long createdLast30 = dossierRepository.count(dossierSpec(regionFilter, null, thirtyDaysAgo));
        long createdPrev30 = dossierRepository.count(dossierSpecBetween(regionFilter, sixtyDaysAgo, thirtyDaysAgo));

        String totalChange = formatPercentChange(createdLast30, createdPrev30, "sur les 30 derniers jours");

        long finalisedLast30 = dossierRepository.count(
                dossierSpec(regionFilter, DossierStatus.FINALISE, thirtyDaysAgo)
        );
        String finalisesChange = finalisedLast30 > 0
                ? "+" + finalisedLast30 + " sur les 30 derniers jours"
                : "Aucun finalisé sur les 30 derniers jours";

        String enCoursPctLabel = totalCount > 0
                ? formatFrenchDecimal(enCoursPct) + " % du total"
                : "0 % du total";

        String tauxRejetChange = aCorrigerCount > 0
                ? aCorrigerCount + " dossier(s) à corriger"
                : "Aucun dossier à corriger";

        double avgDays = computeAverageFinalisationDays(regionFilter, periodStart);
        String dureeChange = finalisesCount > 0
                ? "Moyenne sur " + finalisesCount + " dossier(s) finalisé(s)"
                : "Données insuffisantes";

        List<AdminDashboardResponse.MonthlyVolumeItem> monthly = buildMonthlyVolume(regionFilter, chartMonths, zone);
        String monthlyTrend = computeMonthlyTrend(monthly);

        List<AdminDashboardResponse.FormeDistributionItem> formes =
                buildFormeDistribution(regionFilter, periodStart, totalCount);

        return new AdminDashboardResponse(
                totalCount,
                totalChange,
                enCoursCount,
                enCoursPctLabel,
                finalisesCount,
                finalisesChange,
                rejectionRate,
                tauxRejetChange,
                avgDays,
                dureeChange,
                monthlyTrend,
                monthly,
                formes
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PagedDossierResponse getDossiers(
            String search,
            DossierStatus status,
            LegalFormCode legalForm,
            String region,
            String currentStep,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Dossier> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            boolean isCountQuery = query != null
                    && (Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType()));

            // Charge l'entrepreneur pour le mapping (open-in-view=false)
            if (!isCountQuery) {
                root.fetch("entrepreneur", JoinType.LEFT);
                query.distinct(true);
            }

            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                // Sur count : join explicite ; sur select : réutilise le fetch via get()
                var entrepreneurPath = isCountQuery
                        ? root.join("entrepreneur", JoinType.LEFT)
                        : root.get("entrepreneur");
                Predicate refMatch = cb.like(cb.lower(root.get("reference")), pattern);
                Predicate nameMatch = cb.like(cb.lower(root.get("nomEntreprise")), pattern);
                Predicate entrepreneurNom = cb.like(cb.lower(entrepreneurPath.get("nom")), pattern);
                Predicate entrepreneurPrenom = cb.like(cb.lower(entrepreneurPath.get("prenom")), pattern);
                predicates.add(cb.or(refMatch, nameMatch, entrepreneurNom, entrepreneurPrenom));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("statut"), status));
            }

            if (legalForm != null) {
                predicates.add(cb.equal(root.get("formeJuridique"), legalForm));
            }

            if (region != null && !region.trim().isEmpty() && !"Toutes les régions".equalsIgnoreCase(region.trim())) {
                predicates.add(cb.equal(root.get("region"), region.trim()));
            }

            if (currentStep != null && !currentStep.trim().isEmpty() && !"Toutes les étapes".equalsIgnoreCase(currentStep.trim())) {
                predicates.add(cb.equal(root.get("etapeActuelle"), currentStep.trim()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Dossier> paged = dossierRepository.findAll(spec, pageable);

        List<DossierAdminResponse> mapped = paged.getContent().stream()
                .map(this::mapDossierToResponse)
                .toList();

        return new PagedDossierResponse(
                mapped,
                paged.getNumber(),
                paged.getSize(),
                paged.getTotalElements(),
                paged.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PagedAgentResponse getAgents(
            String search,
            String organization,
            Boolean active,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "nom"));

        Specification<Utilisateur> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("role"), RoleUtilisateur.AGENT));

            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate nomMatch = cb.like(cb.lower(root.get("nom")), pattern);
                Predicate prenomMatch = cb.like(cb.lower(root.get("prenom")), pattern);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), pattern);
                predicates.add(cb.or(nomMatch, prenomMatch, emailMatch));
            }

            if (organization != null && !organization.trim().isEmpty() && !"Tous les organismes".equalsIgnoreCase(organization.trim())) {
                predicates.add(cb.equal(root.get("organisme"), organization.trim()));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("actif"), active));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Utilisateur> paged = utilisateurRepository.findAll(spec, pageable);

        List<AgentAdminResponse> mapped = paged.getContent().stream()
                .map(this::mapAgentToResponse)
                .toList();

        return new PagedAgentResponse(
                mapped,
                paged.getNumber(),
                paged.getSize(),
                paged.getTotalElements(),
                paged.getTotalPages()
        );
    }

    @Override
    public AgentAdminResponse createAgent(AgentCreateAdminRequest req) {
        String normalizedEmail = req.email().trim().toLowerCase();
        if (utilisateurRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException(normalizedEmail);
        }

        Utilisateur agent = new Utilisateur();
        agent.setNom(req.lastName().trim());
        agent.setPrenom(req.firstName().trim());
        agent.setEmail(normalizedEmail);
        agent.setPassword(passwordEncoder.encode(req.password()));
        agent.setRole(RoleUtilisateur.AGENT);
        agent.setActif(true);
        agent.setOrganisme(req.organization().trim());
        agent.setTelephone(req.phone());
        agent.setCin(req.cin());
        agent.setLastActivityAt(Instant.now());

        Utilisateur saved = utilisateurRepository.save(agent);
        return mapAgentToResponse(saved);
    }

    @Override
    public AgentAdminResponse updateAgentStatus(UUID agentId, boolean active) {
        Utilisateur agent = utilisateurRepository.findById(agentId)
                .orElseThrow(() -> new NoSuchElementException("Agent non trouvé avec l'ID: " + agentId));

        if (agent.getRole() != RoleUtilisateur.AGENT) {
            throw new NoSuchElementException("L'utilisateur demandé n'est pas un agent.");
        }

        agent.setActif(active);
        agent.setLastActivityAt(Instant.now());
        Utilisateur saved = utilisateurRepository.saveAndFlush(agent);
        return mapAgentToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportDossiersCsv(
            String search,
            DossierStatus status,
            LegalFormCode legalForm,
            String region
    ) {
        // Appel direct (même classe) : on réutilise la logique paginée déjà transactionnelle
        PagedDossierResponse res = this.getDossiers(search, status, legalForm, region, null, 0, 5000);

        StringBuilder csv = new StringBuilder();
        // BOM UTF-8 pour Excel
        csv.append('\uFEFF');
        csv.append("Référence;Entreprise;Entrepreneur;Forme Juridique;Région;Étape Actuelle;Statut;Date Création;Dernière Mise à Jour\n");

        for (DossierAdminResponse d : res.content()) {
            csv.append(escapeCsv(d.reference())).append(";")
                    .append(escapeCsv(d.companyName())).append(";")
                    .append(escapeCsv(d.entrepreneurName())).append(";")
                    .append(escapeCsv(d.legalFormLabel())).append(";")
                    .append(escapeCsv(d.region())).append(";")
                    .append(escapeCsv(d.currentStep())).append(";")
                    .append(escapeCsv(d.statusLabel())).append(";")
                    .append(escapeCsv(d.createdFormatted())).append(";")
                    .append(escapeCsv(d.updatedFormatted())).append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private DossierAdminResponse mapDossierToResponse(Dossier d) {
        String entrepreneurName = d.getEntrepreneur() != null
                ? d.getEntrepreneur().getPrenom() + " " + d.getEntrepreneur().getNom()
                : "Entrepreneur";

        String cinMasked = d.getEntrepreneur() != null
                ? maskCin(d.getEntrepreneur().getCin())
                : "***";

        String legalLabel = switch (d.getFormeJuridique()) {
            case SARL -> "SARL";
            case SARL_AU -> "SARL-AU";
            case AUTO_ENTREPRENEUR -> "Auto-entrepreneur";
            case SA -> "SA";
        };

        String statusLabel = switch (d.getStatut()) {
            case BROUILLON -> "Brouillon";
            case SOUMIS -> "Soumis";
            case EN_COURS -> "En cours";
            case A_CORRIGER -> "À corriger";
            case FINALISE -> "Finalisé";
        };

        String createdFormatted = d.getCreatedAt() != null ? DATE_FORMATTER.format(d.getCreatedAt()) : "";
        String updatedFormatted = d.getUpdatedAt() != null ? DATE_FORMATTER.format(d.getUpdatedAt()) : "";

        return new DossierAdminResponse(
                d.getId(),
                d.getReference(),
                d.getNomEntreprise() != null ? d.getNomEntreprise() : "Sans nom",
                entrepreneurName,
                cinMasked,
                d.getFormeJuridique(),
                legalLabel,
                d.getRegion(),
                d.getEtapeActuelle() != null ? d.getEtapeActuelle() : "En attente",
                d.getStatut(),
                statusLabel,
                d.getCreatedAt(),
                d.getUpdatedAt(),
                createdFormatted,
                updatedFormatted
        );
    }

    private AgentAdminResponse mapAgentToResponse(Utilisateur a) {
        String fullName = a.getPrenom() + " " + a.getNom();
        String initial1 = a.getPrenom() != null && !a.getPrenom().isEmpty() ? a.getPrenom().substring(0, 1).toUpperCase() : "A";
        String initial2 = a.getNom() != null && !a.getNom().isEmpty() ? a.getNom().substring(0, 1).toUpperCase() : "G";
        String initials = initial1 + initial2;

        String lastActivityFormatted = "—";
        if (a.getLastActivityAt() != null) {
            lastActivityFormatted = FULL_DATE_FORMATTER.format(a.getLastActivityAt());
        }

        return new AgentAdminResponse(
                a.getId(),
                a.getPrenom(),
                a.getNom(),
                fullName,
                initials,
                a.getEmail(),
                a.getOrganisme() != null ? a.getOrganisme() : "Organisme non spécifié",
                a.isActif(),
                lastActivityFormatted,
                a.getLastActivityAt(),
                a.getCreatedAt()
        );
    }

    private String maskCin(String cin) {
        if (cin == null || cin.length() < 4) return "***";
        return cin.substring(0, 2) + "***" + cin.substring(cin.length() - 2);
    }

    private String normalizeRegion(String region) {
        if (region == null || region.isBlank() || "Toutes les régions".equalsIgnoreCase(region.trim())) {
            return null;
        }
        return region.trim();
    }

    private int resolveChartMonths(String period) {
        if (period == null || period.isBlank()) {
            return 6;
        }
        if (period.contains("3 derniers")) {
            return 3;
        }
        if (period.toLowerCase(Locale.FRENCH).contains("année") || period.toLowerCase(Locale.ROOT).contains("annee")) {
            return 12;
        }
        return 6;
    }

    private Instant resolvePeriodStart(String period, ZoneId zone) {
        if (period != null && (period.toLowerCase(Locale.FRENCH).contains("année")
                || period.toLowerCase(Locale.ROOT).contains("annee"))) {
            int year = YearMonth.now(zone).getYear();
            try {
                String digits = period.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    year = Integer.parseInt(digits);
                }
            } catch (NumberFormatException ignored) {
                // garder l'année courante
            }
            return java.time.LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant();
        }
        int months = resolveChartMonths(period);
        return YearMonth.now(zone).minusMonths(months - 1L).atDay(1).atStartOfDay(zone).toInstant();
    }

    private Specification<Dossier> dossierSpec(String region, DossierStatus status, Instant createdAfter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (region != null) {
                predicates.add(cb.equal(root.get("region"), region));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("statut"), status));
            }
            if (createdAfter != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<Dossier> dossierSpecBetween(String region, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (region != null) {
                predicates.add(cb.equal(root.get("region"), region));
            }
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            predicates.add(cb.lessThan(root.get("createdAt"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private double computeAverageFinalisationDays(String region, Instant periodStart) {
        List<Dossier> finalised = dossierRepository.findAll(
                dossierSpec(region, DossierStatus.FINALISE, periodStart)
        );
        if (finalised.isEmpty()) {
            return 0.0;
        }
        double sumDays = 0.0;
        int counted = 0;
        for (Dossier d : finalised) {
            if (d.getCreatedAt() != null && d.getUpdatedAt() != null) {
                long days = ChronoUnit.DAYS.between(d.getCreatedAt(), d.getUpdatedAt());
                sumDays += Math.max(0, days);
                counted++;
            }
        }
        if (counted == 0) {
            return 0.0;
        }
        return Math.round((sumDays / counted) * 10.0) / 10.0;
    }

    private List<AdminDashboardResponse.MonthlyVolumeItem> buildMonthlyVolume(
            String region,
            int months,
            ZoneId zone
    ) {
        YearMonth end = YearMonth.now(zone);
        YearMonth start = end.minusMonths(months - 1L);
        Instant from = start.atDay(1).atStartOfDay(zone).toInstant();

        List<Dossier> dossiers = dossierRepository.findAll(
                dossierSpec(region, null, from)
        );

        Map<YearMonth, Long> grouped = dossiers.stream()
                .filter(d -> d.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        d -> YearMonth.from(d.getCreatedAt().atZone(zone)),
                        Collectors.counting()
                ));

        List<AdminDashboardResponse.MonthlyVolumeItem> items = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = start.plusMonths(i);
            String label = formatMonthLabel(ym, zone);
            int count = grouped.getOrDefault(ym, 0L).intValue();
            items.add(new AdminDashboardResponse.MonthlyVolumeItem(label, count));
        }
        return items;
    }

    private String formatMonthLabel(YearMonth ym, ZoneId zone) {
        String raw = DATE_FORMATTER.format(ym.atDay(1).atStartOfDay(zone).toInstant());
        if (raw.length() >= 3) {
            return raw.substring(0, 1).toUpperCase(Locale.FRENCH) + raw.substring(1, 3).toLowerCase(Locale.FRENCH);
        }
        return raw;
    }

    private String computeMonthlyTrend(List<AdminDashboardResponse.MonthlyVolumeItem> monthly) {
        if (monthly == null || monthly.size() < 2) {
            return "—";
        }
        int last = monthly.get(monthly.size() - 1).count();
        int previous = monthly.get(monthly.size() - 2).count();
        return formatPercentChange(last, previous, "vs mois précédent");
    }

    private String formatPercentChange(long current, long previous, String suffix) {
        if (previous == 0 && current == 0) {
            return "Stable (" + suffix + ")";
        }
        if (previous == 0) {
            return "+" + current + " (" + suffix + ")";
        }
        double change = ((double) (current - previous) / previous) * 100.0;
        String sign = change >= 0 ? "+" : "";
        return sign + formatFrenchDecimal(Math.round(change * 10.0) / 10.0) + " % " + suffix;
    }

    private String formatFrenchDecimal(double value) {
        return String.format(Locale.FRENCH, "%.1f", value).replace(".", ",");
    }

    private List<AdminDashboardResponse.FormeDistributionItem> buildFormeDistribution(
            String region,
            Instant periodStart,
            long total
    ) {
        record FormeMeta(LegalFormCode code, String label, String color) {}

        List<FormeMeta> metas = List.of(
                new FormeMeta(LegalFormCode.SARL, "SARL", "#1F3864"),
                new FormeMeta(LegalFormCode.SARL_AU, "SARL-AU", "#0D9488"),
                new FormeMeta(LegalFormCode.AUTO_ENTREPRENEUR, "Auto-entrepreneur", "#2563EB"),
                new FormeMeta(LegalFormCode.SA, "SA", "#D97706")
        );

        List<AdminDashboardResponse.FormeDistributionItem> items = new ArrayList<>();
        for (FormeMeta meta : metas) {
            long count = dossierRepository.count(dossierSpec(region, null, periodStart).and(
                    (root, query, cb) -> cb.equal(root.get("formeJuridique"), meta.code())
            ));
            int pct = 0;
            if (total > 0) {
                pct = (int) Math.round((count * 100.0) / total);
            }
            items.add(new AdminDashboardResponse.FormeDistributionItem(
                    meta.code().name(),
                    meta.label(),
                    count,
                    pct,
                    meta.color()
            ));
        }
        return items;
    }
}
