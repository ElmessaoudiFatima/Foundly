package com.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.backend.dto.audit.AuditEventDto;
import com.backend.dto.audit.PagedAuditEventDto;
import com.backend.dto.dossier.*;
import com.backend.entity.*;
import com.backend.enums.*;
import com.backend.exception.*;
import com.backend.repository.*;
import com.backend.service.DocumentService;
import com.backend.service.DossierService;
import com.backend.service.NotificationService;
import com.backend.service.WorkflowEngineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Year;
import java.util.*;

@Service
public class DossierServiceImpl implements DossierService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Boolean>> CHECKLIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> RESULT_TYPE = new TypeReference<>() {};

    private final DossierRepository dossierRepository;
    private final SuiviEtapeRepository suiviEtapeRepository;
    private final DocumentRepository documentRepository;
    private final PaiementRepository paiementRepository;
    private final AuditEventRepository auditEventRepository;
    private final WorkflowEngineService workflowEngineService;
    private final NotificationService notificationService;
    private final DocumentService documentService;
    private final AssocieRepository associeRepository;
    private final GerantRepository gerantRepository;

    @Autowired
    public DossierServiceImpl(
            DossierRepository dossierRepository,
            SuiviEtapeRepository suiviEtapeRepository,
            DocumentRepository documentRepository,
            PaiementRepository paiementRepository,
            AuditEventRepository auditEventRepository,
            WorkflowEngineService workflowEngineService,
            NotificationService notificationService,
            DocumentService documentService,
            AssocieRepository associeRepository,
            GerantRepository gerantRepository
    ) {
        this.dossierRepository = dossierRepository;
        this.suiviEtapeRepository = suiviEtapeRepository;
        this.documentRepository = documentRepository;
        this.paiementRepository = paiementRepository;
        this.auditEventRepository = auditEventRepository;
        this.workflowEngineService = workflowEngineService;
        this.notificationService = notificationService;
        this.documentService = documentService;
        this.associeRepository = associeRepository;
        this.gerantRepository = gerantRepository;
    }

    public DossierServiceImpl(
            DossierRepository dossierRepository,
            SuiviEtapeRepository suiviEtapeRepository,
            DocumentRepository documentRepository,
            PaiementRepository paiementRepository,
            AuditEventRepository auditEventRepository,
            WorkflowEngineService workflowEngineService,
            NotificationService notificationService,
            DocumentService documentService
    ) {
        this(dossierRepository, suiviEtapeRepository, documentRepository, paiementRepository,
                auditEventRepository, workflowEngineService, notificationService, documentService, null, null);
    }

    @Override
    @Transactional
    public DossierDetailDto createDossier(DossierCreateRequest request, Utilisateur entrepreneur) {
        BigDecimal capital = request.capitalAmountMad() != null ? request.capitalAmountMad() : BigDecimal.ZERO;
        // RG-AE-01 : Le statut Auto-Entrepreneur ne requiert ni capital social ni blocage bancaire.
        if (request.legalFormCode() == LegalFormCode.AUTO_ENTREPRENEUR) {
            capital = BigDecimal.ZERO;
        } else if (request.legalFormCode() == LegalFormCode.SARL) {
            // Validation spécifique SARL du capital et de la valeur nominale
            validateSarlCapitalAndNominal(capital, request.valeurNominalePart());
        }
        boolean blockageRequired = computeCapitalBlockageRequired(request.legalFormCode(), capital);

        Dossier dossier = new Dossier();
        dossier.setReference(generateUniqueReference());
        dossier.setNomEntreprise(request.proposedCompanyName());
        dossier.setFormeJuridique(request.legalFormCode());
        dossier.setStatut(DossierStatus.BROUILLON);
        dossier.setRegion(request.region());
        dossier.setCapitalMad(capital);
        if (request.legalFormCode() == LegalFormCode.SARL) {
            BigDecimal valNominale = request.valeurNominalePart() != null ? request.valeurNominalePart() : new BigDecimal("100.00");
            dossier.setValeurNominalePart(valNominale);
            dossier.setNombreTotalParts(capital.divide(valNominale, RoundingMode.UNNECESSARY).longValue());
        }
        dossier.setCapitalBlockageRequired(blockageRequired);
        dossier.setDescriptionActivite(request.activityDescription());
        dossier.setAdresseSiege(request.headOfficeAddress());
        dossier.setEntrepreneur(entrepreneur);
        dossier.setWorkflowVersionId(UUID.randomUUID());

        Dossier saved = dossierRepository.save(dossier);

        // Traitement des associés et gérants SARL
        if (request.legalFormCode() == LegalFormCode.SARL && request.associes() != null) {
            processSarlAssociatesAndManagers(saved, request.valeurNominalePart(), request.associes(), request.gerants());
            saved = dossierRepository.save(saved);
        }

        // Initialiser la séquence des étapes de workflow
        List<SuiviEtape> steps = workflowEngineService.initializeStepsForDossier(saved);
        saved.setSteps(steps);
        dossierRepository.save(saved);

        // Audit
        auditEventRepository.save(new AuditEvent(
                saved,
                AuditEventAction.DOSSIER_CREE,
                ActorType.ENTREPRENEUR,
                entrepreneur,
                entrepreneur.getPrenom() + " " + entrepreneur.getNom(),
                null,
                null,
                "Création du dossier de création d'entreprise (" + saved.getFormeJuridique() + ") au statut Brouillon."
        ));

        // Notification in-app
        notificationService.sendNotification(
                entrepreneur,
                saved,
                "Dossier créé avec succès",
                "Votre dossier " + saved.getReference() + " a été initialisé. Complétez votre checklist documentaire pour le soumettre.",
                NotificationChannel.IN_APP,
                NotificationType.DOSSIER_CREE
        );

        return mapToDetailDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DossierDetailDto getDossierDetail(UUID dossierId, Utilisateur user) {
        Dossier dossier = getDossierAndCheckAccess(dossierId, user);
        return mapToDetailDto(dossier);
    }

    @Override
    @Transactional(readOnly = true)
    public DossierSummaryDto getDossierSummary(UUID dossierId, Utilisateur user) {
        Dossier dossier = getDossierAndCheckAccess(dossierId, user);
        return mapToSummaryDto(dossier);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedDossierSummaryDto getEntrepreneurDossiers(UUID entrepreneurId, DossierStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<Dossier> pageResult;

        if (status != null) {
            pageResult = dossierRepository.findByEntrepreneurIdAndStatut(entrepreneurId, status, pageable);
        } else {
            pageResult = dossierRepository.findByEntrepreneurId(entrepreneurId, pageable);
        }

        List<DossierSummaryDto> items = pageResult.getContent().stream()
                .map(this::mapToSummaryDto)
                .toList();

        return new PagedDossierSummaryDto(
                items,
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.getNumber(),
                pageResult.getSize()
        );
    }

    @Override
    @Transactional
    public DossierDetailDto updateDossier(UUID dossierId, DossierUpdateRequest request, Utilisateur user) {
        Dossier dossier = getDossierAndCheckAccess(dossierId, user);

        if (dossier.getStatut() != DossierStatus.BROUILLON && dossier.getStatut() != DossierStatus.A_CORRIGER) {
            throw new InvalidDossierStateException("La modification des informations n'est permise qu'aux statuts Brouillon et À corriger.");
        }

        if (request.legalFormCode() != null && dossier.getStatut() == DossierStatus.BROUILLON) {
            // RG2 : Forme juridique modifiable uniquement en statut Brouillon
            dossier.setFormeJuridique(request.legalFormCode());
        }

        if (request.proposedCompanyName() != null) {
            dossier.setNomEntreprise(request.proposedCompanyName());
        }

        if (request.region() != null) {
            dossier.setRegion(request.region());
        }

        if (request.activityDescription() != null) {
            dossier.setDescriptionActivite(request.activityDescription());
        }

        if (dossier.getFormeJuridique() == LegalFormCode.SARL) {
            BigDecimal newCap = request.capitalAmountMad() != null ? request.capitalAmountMad() : dossier.getCapitalMad();
            BigDecimal newNom = request.valeurNominalePart() != null ? request.valeurNominalePart()
                    : (dossier.getValeurNominalePart() != null ? dossier.getValeurNominalePart() : new BigDecimal("100.00"));
            validateSarlCapitalAndNominal(newCap, newNom);
            dossier.setCapitalMad(newCap);
            dossier.setValeurNominalePart(newNom);
            dossier.setNombreTotalParts(newCap.divide(newNom, RoundingMode.UNNECESSARY).longValue());
            dossier.setCapitalBlockageRequired(computeCapitalBlockageRequired(LegalFormCode.SARL, newCap));

            if (request.associes() != null) {
                updateSarlAssociatesAndManagers(dossier, request.valeurNominalePart(), request.associes(), request.gerants(), user);
            }
        } else if (request.capitalAmountMad() != null) {
            // RG-AE-01 : Le capital est toujours 0 pour un Auto-Entrepreneur.
            BigDecimal newCap = dossier.getFormeJuridique() == LegalFormCode.AUTO_ENTREPRENEUR
                    ? BigDecimal.ZERO
                    : request.capitalAmountMad();
            dossier.setCapitalMad(newCap);
            boolean blockageRequired = computeCapitalBlockageRequired(dossier.getFormeJuridique(), newCap);
            dossier.setCapitalBlockageRequired(blockageRequired);
        }

        if (request.headOfficeAddress() != null) {
            dossier.setAdresseSiege(request.headOfficeAddress());
        }

        Dossier updated = dossierRepository.save(dossier);
        return mapToDetailDto(updated);
    }

    @Override
    @Transactional
    public SubmissionResultDto submitDossier(UUID dossierId, Utilisateur user) {
        Dossier dossier = getDossierAndCheckAccess(dossierId, user);

        if (dossier.getStatut() != DossierStatus.BROUILLON && dossier.getStatut() != DossierStatus.A_CORRIGER) {
            throw new InvalidDossierStateException("Ce dossier a déjà été soumis ou est finalisé.");
        }

        // RG6 : Vérifier que toutes les pièces obligatoires sont déposées
        List<ChecklistItemDto> checklist = documentService.getChecklist(dossierId, user);
        List<String> missingDocs = checklist.stream()
                .filter(item -> item.required() && !item.submitted())
                .map(ChecklistItemDto::label)
                .toList();

        if (!missingDocs.isEmpty()) {
            throw new IncompleteChecklistException(
                    "Impossible de soumettre le dossier : des pièces obligatoires sont manquantes.",
                    missingDocs
            );
        }

        // RG9 : le premier paiement requis doit être confirmé avant la mise en file d'attente de l'agent.
        SuiviEtape payableCurrentStep = suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId).stream()
                .filter(step -> step.getStatut() == StepStatus.EN_COURS && step.isPaymentRequired())
                .findFirst()
                .orElse(null);
        if (payableCurrentStep != null && !paiementRepository.existsByDossierIdAndSuiviEtapeIdAndStatut(
                dossierId, payableCurrentStep.getId(), PaymentStatus.CONFIRME)) {
            throw new IncompleteChecklistException(
                    "Impossible de soumettre le dossier : le paiement requis pour « "
                            + payableCurrentStep.getLibelle() + " » doit être confirmé.",
                    List.of("Paiement requis : " + payableCurrentStep.getLibelle())
            );
        }

        if (dossier.getStatut() == DossierStatus.A_CORRIGER) {
            // Reprise du traitement à l'étape rejetée (RG8)
            workflowEngineService.resumeWorkflowAfterCorrection(dossier);
            return new SubmissionResultDto(
                    dossier.getId(),
                    dossier.getStatut(),
                    new StepReferenceDto(dossier.getCurrentStepCode(), dossier.getEtapeActuelle(), dossier.getCurrentStepOrder()),
                    Instant.now(),
                    "Corrections envoyées. Votre dossier a été réassigné à l'agent administratif pour contrôle."
            );
        }

        // Première soumission depuis l'état Brouillon
        dossier.setStatut(DossierStatus.SOUMIS);
        dossier.setSubmittedAt(Instant.now());
        Dossier saved = dossierRepository.save(dossier);

        // Audit
        auditEventRepository.save(new AuditEvent(
                saved,
                AuditEventAction.DOSSIER_SOUMIS,
                ActorType.ENTREPRENEUR,
                user,
                user.getPrenom() + " " + user.getNom(),
                null,
                null,
                "Soumission officielle du dossier de création d'entreprise au guichet unique."
        ));

        // Notification
        notificationService.sendNotification(
                saved.getEntrepreneur(),
                saved,
                "Dossier soumis avec succès",
                "Votre dossier " + saved.getReference() + " est maintenant en cours d'instruction administrative.",
                NotificationChannel.IN_APP,
                NotificationType.DOSSIER_SOUMIS
        );

        return new SubmissionResultDto(
                saved.getId(),
                saved.getStatut(),
                new StepReferenceDto(saved.getCurrentStepCode(), saved.getEtapeActuelle(), saved.getCurrentStepOrder()),
                saved.getSubmittedAt(),
                "Dossier soumis avec succès. Les administrations partenaires vont procéder à son examen."
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PagedAuditEventDto getAuditEvents(UUID dossierId, int page, int size, Utilisateur user) {
        getDossierAndCheckAccess(dossierId, user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<AuditEvent> pageResult = auditEventRepository.findByDossierIdOrderByOccurredAtDesc(dossierId, pageable);

        List<AuditEventDto> items = pageResult.getContent().stream()
                .map(a -> new AuditEventDto(
                        a.getId(),
                        a.getOccurredAt(),
                        a.getAction(),
                        a.getActorType(),
                        a.getActorNom(),
                        a.getStepTrackingId(),
                        a.getReason(),
                        a.getDetails()
                ))
                .toList();

        return new PagedAuditEventDto(
                items,
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.getNumber(),
                pageResult.getSize()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DossierDetailDto getDossierDetailForAgent(UUID dossierId, String organizationCode) {
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier introuvable : " + dossierId));
        return mapToDetailDto(dossier, organizationCode);
    }

    private Dossier getDossierAndCheckAccess(UUID dossierId, Utilisateur user) {
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier introuvable : " + dossierId));

        if (user.getRole() == RoleUtilisateur.ADMINISTRATEUR) {
            return dossier;
        }

        if (user.getRole() == RoleUtilisateur.ENTREPRENEUR && !dossier.getEntrepreneur().getId().equals(user.getId())) {
            throw new UnauthorizedDossierAccessException("Accès refusé : vous n'êtes pas propriétaire de ce dossier (RG13).");
        }

        return dossier;
    }

    private boolean computeCapitalBlockageRequired(LegalFormCode form, BigDecimal capital) {
        if (form == LegalFormCode.SA) return true;
        if (form == LegalFormCode.AUTO_ENTREPRENEUR) return false;
        // SARL ou SARL_AU : obligatoire si capital > 100 000 MAD (RG15)
        return capital != null && capital.compareTo(new BigDecimal("100000")) > 0;
    }

    private String generateUniqueReference() {
        int year = Year.now().getValue();
        long count = dossierRepository.count() + 1;
        String ref;
        do {
            ref = String.format("FND-%d-%05d", year, count++);
        } while (dossierRepository.findByReference(ref).isPresent());
        return ref;
    }

    private DossierSummaryDto mapToSummaryDto(Dossier d) {
        List<SuiviEtape> steps = suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(d.getId());
        int progression = calculateProgressionPercentage(steps);

        StepReferenceDto currentStepDto = null;
        if (d.getCurrentStepCode() != null) {
            currentStepDto = new StepReferenceDto(d.getCurrentStepCode(), d.getEtapeActuelle(), d.getCurrentStepOrder());
        }

        return new DossierSummaryDto(
                d.getId(),
                d.getReference(),
                d.getNomEntreprise(),
                d.getFormeJuridique(),
                d.getStatut(),
                d.getRegion(),
                currentStepDto,
                d.getCapitalMad(),
                progression,
                d.getRejectionReason(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }

    private DossierDetailDto mapToDetailDto(Dossier d) {
        return mapToDetailDto(d, null);
    }

    private DossierDetailDto mapToDetailDto(Dossier d, String agentOrganization) {
        boolean agentView = agentOrganization != null;
        String organization = agentView ? agentOrganization.toUpperCase() : "";
        List<SuiviEtape> steps = suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(d.getId());
        int progression = calculateProgressionPercentage(steps);

        List<StepTrackingDto> stepDtos = steps.stream().map(s -> new StepTrackingDto(
                s.getId(),
                s.getEtapeCode(),
                s.getLibelle(),
                s.getOrdre(),
                s.getStatut(),
                s.getActionOwner(),
                s.getOrganismeCode(),
                getOrganismeLabel(s.getOrganismeCode()),
                s.getIndicativeDurationDays(),
                s.getFraisMad(),
                s.isPaymentRequired(),
                s.getStartedAt(),
                s.getDecidedAt(),
                s.getMotifRejet(),
                s.getAgent() == null ? null : s.getAgent().getPrenom() + " " + s.getAgent().getNom(),
                readJson(s.getChecklistReviewJson(), CHECKLIST_TYPE),
                readJson(s.getValidationResultJson(), RESULT_TYPE)
        )).toList();

        // Taux de complétude de la checklist
        List<ChecklistItemDto> checklist = documentService.getChecklist(d.getId(), d.getEntrepreneur());
        long requiredCount = checklist.stream().filter(ChecklistItemDto::required).count();
        long submittedCount = checklist.stream().filter(item -> item.required() && item.submitted()).count();
        double completionRate = requiredCount > 0 ? (double) Math.round(((double) submittedCount / requiredCount) * 100.0) : 100.0;

        StepReferenceDto currentStepDto = null;
        if (d.getCurrentStepCode() != null) {
            currentStepDto = new StepReferenceDto(d.getCurrentStepCode(), d.getEtapeActuelle(), d.getCurrentStepOrder());
        }

        String entNom = null;
        String entEmail = null;
        String entCin = null;
        String entTel = null;
        if (d.getEntrepreneur() != null) {
            String prenom = d.getEntrepreneur().getPrenom() != null ? d.getEntrepreneur().getPrenom() : "";
            String nom = d.getEntrepreneur().getNom() != null ? d.getEntrepreneur().getNom() : "";
            entNom = (prenom + " " + nom).trim();
            if (!agentView || Set.of("OMPIC", "CRI", "CNSS").contains(organization)) {
                entEmail = d.getEntrepreneur().getEmail();
                entTel = d.getEntrepreneur().getTelephone();
            }
            if (!agentView || !organization.equals("JOURNAL")) {
                entCin = agentView ? maskCin(d.getEntrepreneur().getCin()) : d.getEntrepreneur().getCin();
            }
        }

        List<AssocieDto> associeDtos = d.getAssocies() == null ? List.of() : d.getAssocies().stream()
                .filter(Associe::isActif)
                .map(a -> new AssocieDto(
                        a.getId(), a.getNom(), a.getPrenom(), a.getCinOuPasseport(),
                        a.getNationalite(), a.getAdresse(), a.getNombreParts(),
                        a.getMontantApport(), a.getPourcentageDetention(), a.isActif()
                )).toList();

        List<GerantDto> gerantDtos = d.getGerants() == null ? List.of() : d.getGerants().stream()
                .filter(Gerant::isActif)
                .map(g -> new GerantDto(
                        g.getId(), g.getAssocie() != null ? g.getAssocie().getId() : null,
                        g.isEstAssocie(), g.getNom(), g.getPrenom(), g.getCinOuPasseport(),
                        g.getNationalite(), g.getAdresse(), g.getModeNomination(),
                        g.getDureeMandat(), g.getPouvoirs(), g.isActif()
                )).toList();

        return new DossierDetailDto(
                d.getId(),
                d.getReference(),
                d.getNomEntreprise(),
                d.getFormeJuridique(),
                d.getStatut(),
                d.getRegion(),
                currentStepDto,
                agentView && organization.equals("JOURNAL") ? null : d.getCapitalMad(),
                progression,
                d.getRejectionReason(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                agentView && organization.equals("JOURNAL") ? null : d.getDescriptionActivite(),
                d.isCapitalBlockageRequired(),
                agentView && !Set.of("OMPIC", "CRI", "TRIBUNAL", "DGI").contains(organization)
                        ? null : d.getAdresseSiege(),
                d.getSubmittedAt(),
                d.getFinalizedAt(),
                completionRate,
                d.getWorkflowVersionId(),
                stepDtos,
                entNom,
                entEmail,
                entCin,
                entTel,
                d.getValeurNominalePart(),
                d.getNombreTotalParts(),
                associeDtos,
                gerantDtos
        );
    }

    private String maskCin(String cin) {
        if (cin == null || cin.isBlank()) return null;
        if (cin.length() <= 4) return "••••";
        return cin.substring(0, 2) + "•".repeat(Math.max(4, cin.length() - 4)) + cin.substring(cin.length() - 2);
    }

    private int calculateProgressionPercentage(List<SuiviEtape> steps) {
        if (steps == null || steps.isEmpty()) return 0;
        long validated = steps.stream().filter(s -> s.getStatut() == StepStatus.VALIDEE).count();
        return (int) Math.round(((double) validated / steps.size()) * 100.0);
    }

    private String getOrganismeLabel(String code) {
        if (code == null) return "Guichet Entrepreneur / Banque";
        return switch (code) {
            case "OMPIC" -> "Office Marocain de la Propriété Industrielle et Commerciale";
            case "CRI" -> "Centre Régional d'Investissement";
            case "TRIBUNAL" -> "Tribunal de Commerce";
            case "DGI" -> "Direction Générale des Impôts";
            case "CNSS" -> "Caisse Nationale de Sécurité Sociale";
            case "JOURNAL" -> "Journal d'Annonces Légales & BO";
            default -> code;
        };
    }

    private <T> Map<String, T> readJson(String value, TypeReference<Map<String, T>> type) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(value, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Impossible de lire les données de validation d'une étape.", exception);
        }
    }

    private void validateSarlCapitalAndNominal(BigDecimal capital, BigDecimal valNominale) {
        if (capital == null || capital.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidDossierStateException("Le capital d'une SARL doit être strictement supérieur à zéro.");
        }
        BigDecimal nominal = valNominale != null ? valNominale : new BigDecimal("100.00");
        if (nominal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidDossierStateException("La valeur nominale des parts sociales doit être strictement positive.");
        }
        if (capital.remainder(nominal).compareTo(BigDecimal.ZERO) != 0) {
            throw new InvalidDossierStateException("Le capital social (" + capital + " MAD) doit être un multiple exact de la valeur nominale des parts (" + nominal + " MAD).");
        }
    }

    private void processSarlAssociatesAndManagers(Dossier dossier, BigDecimal requestNominal, List<AssocieDto> associeDtos, List<GerantDto> gerantDtos) {
        if (associeDtos == null || associeDtos.size() < 2 || associeDtos.size() > 50) {
            throw new InvalidDossierStateException("Une SARL doit comporter entre 2 et 50 associés (règle fonctionnelle de séparation SARL / SARL-AU).");
        }
        BigDecimal nominal = requestNominal != null ? requestNominal
                : (dossier.getValeurNominalePart() != null ? dossier.getValeurNominalePart() : new BigDecimal("100.00"));
        long totalParts = dossier.getNombreTotalParts() != null && dossier.getNombreTotalParts() > 0
                ? dossier.getNombreTotalParts()
                : dossier.getCapitalMad().divide(nominal, RoundingMode.UNNECESSARY).longValue();

        long sumParts = 0;
        BigDecimal sumApports = BigDecimal.ZERO;
        List<Associe> savedAssocies = new ArrayList<>();

        for (AssocieDto dto : associeDtos) {
            if (dto.nombreParts() == null || dto.nombreParts() < 1) {
                throw new InvalidDossierStateException("Chaque associé doit détenir au moins une part sociale.");
            }
            sumParts += dto.nombreParts();
            BigDecimal apport = nominal.multiply(BigDecimal.valueOf(dto.nombreParts()));
            sumApports = sumApports.add(apport);
            BigDecimal pourcentage = BigDecimal.valueOf(dto.nombreParts())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalParts), 2, RoundingMode.HALF_UP);

            Associe associe = new Associe(
                    dossier,
                    dto.nom(),
                    dto.prenom(),
                    dto.cinOuPasseport(),
                    dto.nationalite(),
                    dto.adresse(),
                    dto.nombreParts(),
                    apport,
                    pourcentage
            );
            associe.setActif(true);
            savedAssocies.add(associeRepository.save(associe));
        }

        if (sumParts != totalParts) {
            throw new InvalidDossierStateException("La somme des parts des associés (" + sumParts + ") doit être exactement égale au nombre total de parts (" + totalParts + ").");
        }
        if (sumApports.compareTo(dossier.getCapitalMad()) != 0) {
            throw new InvalidDossierStateException("La somme des apports des associés (" + sumApports + " MAD) doit être égale au capital social (" + dossier.getCapitalMad() + " MAD).");
        }
        dossier.setAssocies(savedAssocies);

        // Gérance
        if (gerantDtos != null) {
            if (gerantDtos.isEmpty()) {
                throw new InvalidDossierStateException("Au moins un gérant doit être désigné pour la SARL.");
            }
            List<Gerant> savedGerants = new ArrayList<>();
            for (GerantDto gDto : gerantDtos) {
                Gerant gerant = new Gerant();
                gerant.setDossier(dossier);
                gerant.setEstAssocie(gDto.estAssocie());
                gerant.setModeNomination(gDto.modeNomination() != null ? gDto.modeNomination() : ModeNominationGerant.STATUTAIRE);
                gerant.setDureeMandat(gDto.dureeMandat() != null ? gDto.dureeMandat() : "Indéterminée");
                gerant.setPouvoirs(gDto.pouvoirs());
                gerant.setActif(true);

                if (gDto.estAssocie()) {
                    Associe matchedAssocie = savedAssocies.stream()
                            .filter(a -> (gDto.associeId() != null && gDto.associeId().equals(a.getId()))
                                    || (a.getCinOuPasseport() != null && a.getCinOuPasseport().equalsIgnoreCase(gDto.cinOuPasseport())))
                            .findFirst()
                            .orElseThrow(() -> new InvalidDossierStateException("Le gérant associé doit obligatoirement correspondre à un associé du même dossier."));
                    gerant.setAssocie(matchedAssocie);
                    gerant.setNom(matchedAssocie.getNom());
                    gerant.setPrenom(matchedAssocie.getPrenom());
                    gerant.setCinOuPasseport(matchedAssocie.getCinOuPasseport());
                    gerant.setNationalite(matchedAssocie.getNationalite());
                    gerant.setAdresse(matchedAssocie.getAdresse());
                } else {
                    if (gDto.associeId() != null) {
                        throw new InvalidDossierStateException("Un gérant non associé ne peut pas référencer un associé.");
                    }
                    if (gDto.nom() == null || gDto.nom().isBlank() || gDto.prenom() == null || gDto.prenom().isBlank()
                            || gDto.cinOuPasseport() == null || gDto.cinOuPasseport().isBlank()
                            || gDto.nationalite() == null || gDto.nationalite().isBlank()
                            || gDto.adresse() == null || gDto.adresse().isBlank()) {
                        throw new InvalidDossierStateException("Toutes les informations d'identification du gérant tiers sont obligatoires.");
                    }
                    gerant.setNom(gDto.nom());
                    gerant.setPrenom(gDto.prenom());
                    gerant.setCinOuPasseport(gDto.cinOuPasseport());
                    gerant.setNationalite(gDto.nationalite());
                    gerant.setAdresse(gDto.adresse());
                }
                savedGerants.add(gerantRepository.save(gerant));
            }
            dossier.setGerants(savedGerants);
        }
    }

    private void updateSarlAssociatesAndManagers(Dossier dossier, BigDecimal requestNominal, List<AssocieDto> associeDtos, List<GerantDto> gerantDtos, Utilisateur user) {
        if (associeDtos == null || associeDtos.size() < 2 || associeDtos.size() > 50) {
            throw new InvalidDossierStateException("Une SARL doit comporter entre 2 et 50 associés (règle fonctionnelle de séparation SARL / SARL-AU).");
        }
        BigDecimal nominal = requestNominal != null ? requestNominal
                : (dossier.getValeurNominalePart() != null ? dossier.getValeurNominalePart() : new BigDecimal("100.00"));
        long totalParts = dossier.getNombreTotalParts() != null && dossier.getNombreTotalParts() > 0
                ? dossier.getNombreTotalParts()
                : dossier.getCapitalMad().divide(nominal, RoundingMode.UNNECESSARY).longValue();

        long sumParts = 0;
        BigDecimal sumApports = BigDecimal.ZERO;

        List<Associe> existingAssocies = associeRepository.findByDossierId(dossier.getId());
        List<Associe> updatedAssocies = new ArrayList<>();

        // Repérer et désactiver logiquement les associés retirés (RG12)
        Set<String> newCins = new HashSet<>();
        Set<UUID> newIds = new HashSet<>();
        for (AssocieDto dto : associeDtos) {
            if (dto.cinOuPasseport() != null) newCins.add(dto.cinOuPasseport().toLowerCase());
            if (dto.id() != null) newIds.add(dto.id());
        }

        for (Associe existing : existingAssocies) {
            boolean retained = (existing.getId() != null && newIds.contains(existing.getId()))
                    || (existing.getCinOuPasseport() != null && newCins.contains(existing.getCinOuPasseport().toLowerCase()));
            if (!retained && existing.isActif()) {
                existing.setActif(false);
                associeRepository.save(existing);
                // Audit RG12
                auditEventRepository.save(new AuditEvent(
                        dossier,
                        AuditEventAction.DOSSIER_MODIFIE,
                        ActorType.ENTREPRENEUR,
                        user,
                        user.getPrenom() + " " + user.getNom(),
                        null,
                        null,
                        "Retrait de l'associé " + existing.getPrenom() + " " + existing.getNom()
                                + " (CIN: " + existing.getCinOuPasseport() + ") du dossier."
                ));
                // Si un gérant référençait cet associé, le désactiver également
                List<Gerant> gerantsLies = gerantRepository.findByDossierId(dossier.getId());
                for (Gerant g : gerantsLies) {
                    if (g.getAssocie() != null && g.getAssocie().getId().equals(existing.getId()) && g.isActif()) {
                        g.setActif(false);
                        gerantRepository.save(g);
                    }
                }
            }
        }

        // Mettre à jour ou ajouter les associés actifs
        for (AssocieDto dto : associeDtos) {
            if (dto.nombreParts() == null || dto.nombreParts() < 1) {
                throw new InvalidDossierStateException("Chaque associé doit détenir au moins une part sociale.");
            }
            sumParts += dto.nombreParts();
            BigDecimal apport = nominal.multiply(BigDecimal.valueOf(dto.nombreParts()));
            sumApports = sumApports.add(apport);
            BigDecimal pourcentage = BigDecimal.valueOf(dto.nombreParts())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalParts), 2, RoundingMode.HALF_UP);

            Associe associe = existingAssocies.stream()
                    .filter(a -> (dto.id() != null && dto.id().equals(a.getId()))
                            || (dto.cinOuPasseport() != null && dto.cinOuPasseport().equalsIgnoreCase(a.getCinOuPasseport())))
                    .findFirst()
                    .orElseGet(() -> {
                        Associe newA = new Associe();
                        newA.setDossier(dossier);
                        return newA;
                    });

            associe.setNom(dto.nom());
            associe.setPrenom(dto.prenom());
            associe.setCinOuPasseport(dto.cinOuPasseport());
            associe.setNationalite(dto.nationalite());
            associe.setAdresse(dto.adresse());
            associe.setNombreParts(dto.nombreParts());
            associe.setMontantApport(apport);
            associe.setPourcentageDetention(pourcentage);
            associe.setActif(true);
            updatedAssocies.add(associeRepository.save(associe));
        }

        if (sumParts != totalParts) {
            throw new InvalidDossierStateException("La somme des parts des associés (" + sumParts + ") doit être exactement égale au nombre total de parts (" + totalParts + ").");
        }
        if (sumApports.compareTo(dossier.getCapitalMad()) != 0) {
            throw new InvalidDossierStateException("La somme des apports des associés (" + sumApports + " MAD) doit être égale au capital social (" + dossier.getCapitalMad() + " MAD).");
        }
        dossier.setAssocies(updatedAssocies);

        // Gérance
        if (gerantDtos != null) {
            if (gerantDtos.isEmpty()) {
                throw new InvalidDossierStateException("Au moins un gérant doit être désigné pour la SARL.");
            }
            List<Gerant> existingGerants = gerantRepository.findByDossierId(dossier.getId());
            List<Gerant> updatedGerants = new ArrayList<>();

            Set<String> newGerantCins = new HashSet<>();
            for (GerantDto gd : gerantDtos) {
                if (gd.cinOuPasseport() != null) newGerantCins.add(gd.cinOuPasseport().toLowerCase());
            }
            for (Gerant eg : existingGerants) {
                if (eg.getCinOuPasseport() != null && !newGerantCins.contains(eg.getCinOuPasseport().toLowerCase()) && eg.isActif()) {
                    eg.setActif(false);
                    gerantRepository.save(eg);
                }
            }

            for (GerantDto gDto : gerantDtos) {
                Gerant gerant = existingGerants.stream()
                        .filter(g -> (gDto.id() != null && gDto.id().equals(g.getId()))
                                || (gDto.cinOuPasseport() != null && gDto.cinOuPasseport().equalsIgnoreCase(g.getCinOuPasseport())))
                        .findFirst()
                        .orElseGet(() -> {
                            Gerant newG = new Gerant();
                            newG.setDossier(dossier);
                            return newG;
                        });

                gerant.setEstAssocie(gDto.estAssocie());
                gerant.setModeNomination(gDto.modeNomination() != null ? gDto.modeNomination() : ModeNominationGerant.STATUTAIRE);
                gerant.setDureeMandat(gDto.dureeMandat() != null ? gDto.dureeMandat() : "Indéterminée");
                gerant.setPouvoirs(gDto.pouvoirs());
                gerant.setActif(true);

                if (gDto.estAssocie()) {
                    Associe matchedAssocie = updatedAssocies.stream()
                            .filter(Associe::isActif)
                            .filter(a -> (gDto.associeId() != null && gDto.associeId().equals(a.getId()))
                                    || (a.getCinOuPasseport() != null && a.getCinOuPasseport().equalsIgnoreCase(gDto.cinOuPasseport())))
                            .findFirst()
                            .orElseThrow(() -> new InvalidDossierStateException("Le gérant associé doit obligatoirement correspondre à un associé du même dossier."));
                    gerant.setAssocie(matchedAssocie);
                    gerant.setNom(matchedAssocie.getNom());
                    gerant.setPrenom(matchedAssocie.getPrenom());
                    gerant.setCinOuPasseport(matchedAssocie.getCinOuPasseport());
                    gerant.setNationalite(matchedAssocie.getNationalite());
                    gerant.setAdresse(matchedAssocie.getAdresse());
                } else {
                    if (gDto.associeId() != null) {
                        throw new InvalidDossierStateException("Un gérant non associé ne peut pas référencer un associé.");
                    }
                    if (gDto.nom() == null || gDto.nom().isBlank() || gDto.prenom() == null || gDto.prenom().isBlank()
                            || gDto.cinOuPasseport() == null || gDto.cinOuPasseport().isBlank()
                            || gDto.nationalite() == null || gDto.nationalite().isBlank()
                            || gDto.adresse() == null || gDto.adresse().isBlank()) {
                        throw new InvalidDossierStateException("Toutes les informations d'identification du gérant tiers sont obligatoires.");
                    }
                    gerant.setAssocie(null);
                    gerant.setNom(gDto.nom());
                    gerant.setPrenom(gDto.prenom());
                    gerant.setCinOuPasseport(gDto.cinOuPasseport());
                    gerant.setNationalite(gDto.nationalite());
                    gerant.setAdresse(gDto.adresse());
                }
                updatedGerants.add(gerantRepository.save(gerant));
            }
            dossier.setGerants(updatedGerants);
        }
    }
}
