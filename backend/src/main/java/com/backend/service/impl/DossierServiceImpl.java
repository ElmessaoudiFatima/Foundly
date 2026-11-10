package com.backend.service.impl;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.*;

@Service
public class DossierServiceImpl implements DossierService {

    private final DossierRepository dossierRepository;
    private final SuiviEtapeRepository suiviEtapeRepository;
    private final DocumentRepository documentRepository;
    private final PaiementRepository paiementRepository;
    private final AuditEventRepository auditEventRepository;
    private final WorkflowEngineService workflowEngineService;
    private final NotificationService notificationService;
    private final DocumentService documentService;

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
        this.dossierRepository = dossierRepository;
        this.suiviEtapeRepository = suiviEtapeRepository;
        this.documentRepository = documentRepository;
        this.paiementRepository = paiementRepository;
        this.auditEventRepository = auditEventRepository;
        this.workflowEngineService = workflowEngineService;
        this.notificationService = notificationService;
        this.documentService = documentService;
    }

    @Override
    @Transactional
    public DossierDetailDto createDossier(DossierCreateRequest request, Utilisateur entrepreneur) {
        BigDecimal capital = request.capitalAmountMad() != null ? request.capitalAmountMad() : BigDecimal.ZERO;
        boolean blockageRequired = computeCapitalBlockageRequired(request.legalFormCode(), capital);

        Dossier dossier = new Dossier();
        dossier.setReference(generateUniqueReference());
        dossier.setNomEntreprise(request.proposedCompanyName());
        dossier.setFormeJuridique(request.legalFormCode());
        dossier.setStatut(DossierStatus.BROUILLON);
        dossier.setRegion(request.region());
        dossier.setCapitalMad(capital);
        dossier.setCapitalBlockageRequired(blockageRequired);
        dossier.setDescriptionActivite(request.activityDescription());
        dossier.setAdresseSiege(request.headOfficeAddress());
        dossier.setEntrepreneur(entrepreneur);
        dossier.setWorkflowVersionId(UUID.randomUUID());

        Dossier saved = dossierRepository.save(dossier);

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

        if (request.capitalAmountMad() != null) {
            dossier.setCapitalMad(request.capitalAmountMad());
            boolean blockageRequired = computeCapitalBlockageRequired(dossier.getFormeJuridique(), request.capitalAmountMad());
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
                s.getMotifRejet()
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

        return new DossierDetailDto(
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
                d.getUpdatedAt(),
                d.getDescriptionActivite(),
                d.isCapitalBlockageRequired(),
                d.getAdresseSiege(),
                d.getSubmittedAt(),
                d.getFinalizedAt(),
                completionRate,
                d.getWorkflowVersionId(),
                stepDtos
        );
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
}
