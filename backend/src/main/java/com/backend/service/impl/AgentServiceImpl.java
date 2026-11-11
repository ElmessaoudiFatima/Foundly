package com.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.backend.dto.agent.AgentDocumentReviewRequest;
import com.backend.dto.agent.AgentHistoryItemDto;
import com.backend.dto.agent.AgentQueueItemDto;
import com.backend.dto.agent.AgentRejectRequest;
import com.backend.dto.agent.AgentValidateRequest;
import com.backend.dto.audit.AuditEventDto;
import com.backend.dto.audit.PagedAuditEventDto;
import com.backend.entity.Dossier;
import com.backend.entity.AuditEvent;
import com.backend.entity.DocumentEntity;
import com.backend.entity.SuiviEtape;
import com.backend.entity.Utilisateur;
import com.backend.enums.ActorType;
import com.backend.enums.AuditEventAction;
import com.backend.enums.DossierStatus;
import com.backend.enums.DocumentReviewStatus;
import com.backend.enums.StepStatus;
import com.backend.exception.AgentClaimConflictException;
import com.backend.exception.DossierNotFoundException;
import com.backend.exception.InvalidDossierStateException;
import com.backend.exception.UnauthorizedDossierAccessException;
import com.backend.repository.DossierRepository;
import com.backend.repository.AuditEventRepository;
import com.backend.repository.DocumentRepository;
import com.backend.repository.SuiviEtapeRepository;
import com.backend.service.AgentService;
import com.backend.service.WorkflowEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Implémentation de la file et des décisions agent (RG5, RG7, RG8).
 */
@Service
public class AgentServiceImpl implements AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);

    private final DossierRepository dossierRepository;
    private final SuiviEtapeRepository suiviEtapeRepository;
    private final DocumentRepository documentRepository;
    private final AuditEventRepository auditEventRepository;
    private final WorkflowEngineService workflowEngineService;
    private final ObjectMapper objectMapper;

    public AgentServiceImpl(
            DossierRepository dossierRepository,
            SuiviEtapeRepository suiviEtapeRepository,
            DocumentRepository documentRepository,
            AuditEventRepository auditEventRepository,
            ObjectMapper objectMapper,
            WorkflowEngineService workflowEngineService
    ) {
        this.dossierRepository = dossierRepository;
        this.suiviEtapeRepository = suiviEtapeRepository;
        this.documentRepository = documentRepository;
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
        this.workflowEngineService = workflowEngineService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentQueueItemDto> getQueue(Utilisateur agent) {
        String agentOrganisme = resolveOrganismeCode(agent);
        log.info("Chargement de la file agent : '{}' - organisme '{}'", agent.getEmail(), agentOrganisme);

        return suiviEtapeRepository.findActiveStepsWithDossier(StepStatus.EN_COURS).stream()
                .filter(step -> isCurrentStepForOrganisme(step, agentOrganisme))
                .map(step -> toQueueItem(step, agent))
                .sorted(Comparator.comparing(AgentQueueItemDto::etapeStartedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    @Transactional
    public AgentQueueItemDto takeInCharge(UUID dossierId, Utilisateur agent) {
        SuiviEtape step = findCurrentStepForUpdate(dossierId);
        requireCurrentStepForAgent(step, agent);

        Utilisateur claimant = step.getClaimedByAgent();
        if (claimant != null && !claimant.getId().equals(agent.getId())) {
            throw new AgentClaimConflictException("Ce dossier est déjà pris en charge par "
                    + claimant.getPrenom() + " " + claimant.getNom() + ".");
        }

        if (claimant == null) {
            step.setClaimedByAgent(agent);
            step.setClaimedAt(Instant.now());
            step = suiviEtapeRepository.save(step);
        }
        return toQueueItem(step, agent);
    }

    @Override
    @Transactional
    public void releaseClaim(UUID dossierId, Utilisateur agent) {
        SuiviEtape step = findCurrentStepForUpdate(dossierId);
        requireCurrentStepForAgent(step, agent);

        Utilisateur claimant = step.getClaimedByAgent();
        if (claimant == null) {
            return;
        }
        if (!claimant.getId().equals(agent.getId())) {
            throw new UnauthorizedDossierAccessException("Seul l'agent ayant pris en charge ce dossier peut le libérer.");
        }

        step.setClaimedByAgent(null);
        step.setClaimedAt(null);
        suiviEtapeRepository.save(step);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertDossierAccess(UUID dossierId, Utilisateur agent) {
        String agentOrganisme = resolveOrganismeCode(agent);
        SuiviEtape currentStep = suiviEtapeRepository
                .findFirstByDossierIdAndStatut(dossierId, StepStatus.EN_COURS)
                .orElse(null);

        if (currentStep != null) {
            if (!isCurrentStepForOrganisme(currentStep, agentOrganisme)) {
                throw new UnauthorizedDossierAccessException(
                        "Accès refusé : l'étape courante de ce dossier ne relève pas de votre organisme (RG5).");
            }
            return;
        }

        boolean previouslyHandledByAgent = suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId).stream()
                .anyMatch(step -> step.getAgent() != null
                        && step.getAgent().getId().equals(agent.getId())
                        && step.getActionOwner() == ActorType.AGENT
                        && matchesOrganisme(agentOrganisme, step.getOrganismeCode()));
        if (!previouslyHandledByAgent) {
            throw new UnauthorizedDossierAccessException(
                    "Accès refusé : l'étape courante de ce dossier ne relève pas de votre organisme (RG5).");
        }
    }

    @Override
    @Transactional
    public void assertClaimedByAgent(UUID dossierId, Utilisateur agent) {
        requireClaimOwnership(findCurrentStepForUpdate(dossierId), agent);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertDocumentAccess(UUID dossierId, UUID documentId, Utilisateur agent) {
        assertDossierAccess(dossierId, agent);
        DocumentEntity document = documentRepository.findByIdAndDossierId(documentId, dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Document introuvable : " + documentId));
        if (!isDocumentTypeAllowedForAgent(document.getDocumentTypeCode(), agent)) {
            throw new UnauthorizedDossierAccessException("Cette pièce n'est pas accessible à votre organisme.");
        }
    }

    @Override
    public boolean isDocumentTypeAllowedForAgent(String documentTypeCode, Utilisateur agent) {
        return applicableDocumentTypes(resolveOrganismeCode(agent)).contains(documentTypeCode);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertHistoricalDossierAccess(UUID dossierId, Utilisateur agent) {
        dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier introuvable : " + dossierId));
        boolean hasDecisionAudit = auditEventRepository
                .findByDossierIdAndActorIdAndActionInOrderByOccurredAtDesc(
                        dossierId, agent.getId(), List.of(AuditEventAction.ETAPE_VALIDEE, AuditEventAction.ETAPE_REJETEE))
                .stream().anyMatch(event -> event.getStepTrackingId() != null);
        boolean hasDecisionStep = suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId).stream()
                .anyMatch(step -> step.getAgent() != null
                        && step.getAgent().getId().equals(agent.getId())
                        && (step.getStatut() == StepStatus.VALIDEE || step.getStatut() == StepStatus.REJETEE));
        if (!hasDecisionAudit && !hasDecisionStep) {
            throw new UnauthorizedDossierAccessException(
                    "Accès refusé : aucun historique de décision de cet agent pour ce dossier.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void assertHistoricalDocumentAccess(UUID dossierId, UUID documentId, Utilisateur agent) {
        assertHistoricalDossierAccess(dossierId, agent);
        DocumentEntity document = documentRepository.findByIdAndDossierId(documentId, dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Document introuvable : " + documentId));
        if (!isDocumentTypeAllowedForAgent(document.getDocumentTypeCode(), agent)) {
            throw new UnauthorizedDossierAccessException("Cette pièce n'est pas accessible à votre organisme.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PagedAuditEventDto getHistoricalAuditEvents(UUID dossierId, int page, int size, Utilisateur agent) {
        assertHistoricalDossierAccess(dossierId, agent);
        List<UUID> stepIds = historicalDecisionStepIds(dossierId, agent);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        Page<AuditEvent> eventPage = stepIds.isEmpty()
                ? Page.empty(pageable)
                : auditEventRepository.findByStepTrackingIdInOrderByOccurredAtDesc(stepIds, pageable);
        List<AuditEventDto> items = eventPage.getContent().stream()
                .map(event -> new AuditEventDto(event.getId(), event.getOccurredAt(), event.getAction(),
                        event.getActorType(), event.getActorNom(), event.getStepTrackingId(),
                        event.getReason(), event.getDetails()))
                .toList();
        return new PagedAuditEventDto(items, eventPage.getTotalElements(), eventPage.getTotalPages(),
                eventPage.getNumber(), eventPage.getSize());
    }

    @Override
    @Transactional
    public void reviewDocument(UUID dossierId, UUID documentId, AgentDocumentReviewRequest request, Utilisateur agent) {
        SuiviEtape currentStep = findCurrentStepForUpdate(dossierId);
        requireClaimOwnership(currentStep, agent);
        DocumentEntity document = documentRepository.findByIdAndDossierId(documentId, dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Document introuvable : " + documentId));
        if (!isDocumentTypeAllowedForAgent(document.getDocumentTypeCode(), agent)) {
            throw new UnauthorizedDossierAccessException("Cette pièce n'est pas accessible à votre organisme.");
        }
        if (request.status() == null) {
            throw new InvalidDossierStateException("Le statut de conformité du document est obligatoire.");
        }
        String comment = request.comment() == null ? null : request.comment().trim();
        if (request.status() == DocumentReviewStatus.NON_CONFORME && (comment == null || comment.isBlank())) {
            throw new InvalidDossierStateException("Un commentaire est obligatoire pour un document non conforme.");
        }

        document.setReviewStatus(request.status());
        document.setReviewComment(comment);
        document.setReviewedByAgent(agent);
        document.setReviewedAt(Instant.now());
        documentRepository.save(document);
        auditEventRepository.save(new AuditEvent(
                currentStep.getDossier(),
                AuditEventAction.DOCUMENT_VERIFIE,
                ActorType.AGENT,
                agent,
                agent.getPrenom() + " " + agent.getNom(),
                currentStep.getId(),
                comment,
                "Document " + document.getOriginalFileName() + " marqué " + request.status()
        ));
    }

    @Override
    @Transactional
    public void auditDocumentConsultation(UUID dossierId, UUID documentId, Utilisateur agent) {
        DocumentEntity document = documentRepository.findByIdAndDossierId(documentId, dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Document introuvable : " + documentId));
        SuiviEtape activeStep = suiviEtapeRepository.findFirstByDossierIdAndStatut(dossierId, StepStatus.EN_COURS)
                .orElse(null);
        if (activeStep == null || !isCurrentStepForOrganisme(activeStep, resolveOrganismeCode(agent))) {
            assertHistoricalDossierAccess(dossierId, agent);
        } else {
            assertDossierAccess(dossierId, agent);
        }
        if (!isDocumentTypeAllowedForAgent(document.getDocumentTypeCode(), agent)) {
            throw new UnauthorizedDossierAccessException("Cette pièce n'est pas accessible à votre organisme.");
        }
        SuiviEtape currentStep = activeStep != null && isCurrentStepForOrganisme(activeStep, resolveOrganismeCode(agent))
                ? activeStep
                : suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId).stream()
                .filter(step -> step.getAgent() != null && step.getAgent().getId().equals(agent.getId())
                        && (step.getStatut() == StepStatus.VALIDEE || step.getStatut() == StepStatus.REJETEE))
                .max(Comparator.comparing(SuiviEtape::getOrdre))
                .orElseThrow(() -> new InvalidDossierStateException("Aucune étape accessible pour journaliser la consultation."));
        auditEventRepository.save(new AuditEvent(
                currentStep.getDossier(),
                AuditEventAction.DOCUMENT_CONSULTE,
                ActorType.AGENT,
                agent,
                agent.getPrenom() + " " + agent.getNom(),
                currentStep.getId(),
                null,
                "Consultation du document " + document.getOriginalFileName()
        ));
    }

    @Override
    @Transactional
    public void auditHistoricalDocumentConsultation(UUID dossierId, UUID documentId, Utilisateur agent) {
        assertHistoricalDocumentAccess(dossierId, documentId, agent);
        DocumentEntity document = documentRepository.findByIdAndDossierId(documentId, dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Document introuvable : " + documentId));
        SuiviEtape historicalStep = suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId).stream()
                .filter(step -> step.getAgent() != null && step.getAgent().getId().equals(agent.getId())
                        && (step.getStatut() == StepStatus.VALIDEE || step.getStatut() == StepStatus.REJETEE))
                .max(Comparator.comparing(SuiviEtape::getOrdre))
                .orElseGet(() -> historicalStepFromAudit(dossierId, agent));
        auditEventRepository.save(new AuditEvent(
                historicalStep.getDossier(),
                AuditEventAction.DOCUMENT_CONSULTE,
                ActorType.AGENT,
                agent,
                agent.getPrenom() + " " + agent.getNom(),
                historicalStep.getId(),
                null,
                "Consultation en lecture seule du document " + document.getOriginalFileName()
        ));
    }

    @Override
    @Transactional
    public void validateStep(UUID dossierId, AgentValidateRequest request, Utilisateur agent) {
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier introuvable : " + dossierId));
        SuiviEtape currentStep = findCurrentStepForUpdate(dossierId);
        requireClaimOwnership(currentStep, agent);

        Set<String> checklistKeys = requiredChecklistKeys(currentStep.getOrganismeCode());
        if (request == null || request.checklist() == null
                || !request.checklist().keySet().equals(checklistKeys)
                || request.checklist().values().stream().anyMatch(checked -> !Boolean.TRUE.equals(checked))) {
            throw new InvalidDossierStateException("Toutes les vérifications de la checklist doivent être confirmées.");
        }
        Set<String> expectedResultKeys = requiredResultKeys(currentStep.getOrganismeCode(), dossier.isCapitalBlockageRequired());
        if (request.stepResult() == null || !request.stepResult().keySet().equals(expectedResultKeys)
                || request.stepResult().values().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new InvalidDossierStateException("Tous les champs du résultat de l'étape doivent être renseignés.");
        }
        if ("CRI".equalsIgnoreCase(currentStep.getOrganismeCode()) && dossier.isCapitalBlockageRequired()
                && List.of("capitalAmountConfirmed", "companyNameConfirmed", "partnersIdentityConfirmed").stream()
                .anyMatch(key -> !"true".equalsIgnoreCase(request.stepResult().get(key)))) {
            throw new InvalidDossierStateException("Les trois contrôles de l'attestation de blocage doivent être confirmés.");
        }
        List<DocumentEntity> documents = documentRepository.findByDossierIdOrderByUploadedAtDesc(dossierId);
        Set<String> applicableTypes = applicableDocumentTypes(currentStep.getOrganismeCode());
        List<DocumentEntity> applicableDocuments = documents.stream()
                .filter(document -> applicableTypes.contains(document.getDocumentTypeCode()))
                .toList();
        Set<String> requiredTypes = requiredDocumentTypes(currentStep.getOrganismeCode(), dossier.isCapitalBlockageRequired());
        Set<String> uploadedTypes = new HashSet<>();
        applicableDocuments.forEach(document -> uploadedTypes.add(document.getDocumentTypeCode()));
        if (applicableDocuments.isEmpty()
                || !uploadedTypes.containsAll(requiredTypes)
                || applicableDocuments.stream().anyMatch(document -> document.getReviewStatus() != DocumentReviewStatus.CONFORME)) {
            throw new InvalidDossierStateException("Toutes les pièces doivent être contrôlées et conformes avant validation.");
        }
        currentStep.setChecklistReviewJson(writeJson(request.checklist()));
        Map<String, String> stepResult = new HashMap<>(request.stepResult());
        if ("OMPIC".equalsIgnoreCase(currentStep.getOrganismeCode())) {
            try {
                stepResult.put("expiryDate", LocalDate.parse(stepResult.get("issuedAt")).plusDays(90).toString());
            } catch (DateTimeParseException | NullPointerException exception) {
                throw new InvalidDossierStateException("La date de délivrance du certificat est invalide.");
            }
        }
        currentStep.setValidationResultJson(writeJson(stepResult));
        currentStep.setAgent(agent);
        clearClaim(currentStep);
        suiviEtapeRepository.save(currentStep);

        workflowEngineService.advanceWorkflow(dossier, request != null ? request.notes() : null, agent);
    }

    private Set<String> requiredChecklistKeys(String organization) {
        return switch (normalizeOrganismeCode(organization)) {
            case "OMPIC" -> Set.of("nameAvailability", "applicantIdentity", "documentsComplete");
            case "CRI" -> Set.of("companyFileComplete", "companyDetailsConsistent", "documentsComplete");
            case "TRIBUNAL" -> Set.of("companyNameChecked", "statutesCompliant", "registrationComplete");
            case "DGI" -> Set.of("taxIdentityVerified", "activityTaxChecked", "supportingDocuments");
            case "CNSS" -> Set.of("employerInformationVerified", "legalStatusVerified", "affiliationRequestComplete");
            case "JOURNAL" -> Set.of("publicationTextVerified", "publicationDetailsVerified", "publicationProofReady");
            default -> throw new InvalidDossierStateException("Aucune checklist n'est configurée pour cet organisme.");
        };
    }

    private Set<String> requiredResultKeys(String organization, boolean capitalBlockageRequired) {
        return switch (normalizeOrganismeCode(organization)) {
            case "OMPIC" -> Set.of("reservedName", "issuedAt", "certificateNumber");
            case "CRI" -> capitalBlockageRequired
                    ? Set.of("depositReceiptNumber", "capitalAmountConfirmed", "companyNameConfirmed", "partnersIdentityConfirmed")
                    : Set.of("depositReceiptNumber");
            case "TRIBUNAL" -> Set.of("commercialRegisterNumber", "ice");
            case "DGI" -> Set.of("taxIdentifier", "professionalTaxCertificate");
            case "CNSS" -> Set.of("affiliationNumber");
            case "JOURNAL" -> Set.of("publicationCertificate");
            default -> throw new InvalidDossierStateException("Aucun résultat n'est configuré pour cet organisme.");
        };
    }

    private Set<String> applicableDocumentTypes(String organization) {
        return switch (normalizeOrganismeCode(organization)) {
            case "OMPIC" -> Set.of("CIN", "JUSTIFICATIF_SIEGE", "AUTRE");
            case "CRI" -> Set.of("CIN", "JUSTIFICATIF_SIEGE", "STATUTS_SIGNES",
                    "ATTESTATION_BLOCAGE_CAPITAL", "FORMULAIRE_UNIQUE", "PV_NOMINATION_GERANT", "AUTRE");
            case "TRIBUNAL" -> Set.of("CIN", "JUSTIFICATIF_SIEGE", "STATUTS_SIGNES",
                    "FORMULAIRE_UNIQUE", "PV_NOMINATION_GERANT", "AUTRE");
            case "DGI" -> Set.of("CIN", "JUSTIFICATIF_SIEGE", "FORMULAIRE_UNIQUE", "AUTRE");
            case "CNSS" -> Set.of("CIN", "STATUTS_SIGNES", "FORMULAIRE_UNIQUE", "AUTRE");
            case "JOURNAL" -> Set.of("CERTIFICAT_NEGATIF", "STATUTS_SIGNES", "FORMULAIRE_UNIQUE",
                    "ATTESTATION_PARUTION", "AUTRE");
            default -> Set.of();
        };
    }

    private Set<String> requiredDocumentTypes(String organization, boolean capitalBlockageRequired) {
        return switch (normalizeOrganismeCode(organization)) {
            case "OMPIC" -> Set.of("CIN");
            case "CRI" -> capitalBlockageRequired
                    ? Set.of("CIN", "STATUTS_SIGNES", "FORMULAIRE_UNIQUE", "ATTESTATION_BLOCAGE_CAPITAL")
                    : Set.of("CIN", "STATUTS_SIGNES", "FORMULAIRE_UNIQUE");
            case "TRIBUNAL" -> Set.of("CIN", "JUSTIFICATIF_SIEGE", "STATUTS_SIGNES", "FORMULAIRE_UNIQUE");
            case "DGI" -> Set.of("CIN", "JUSTIFICATIF_SIEGE", "FORMULAIRE_UNIQUE");
            case "CNSS" -> Set.of("CIN", "STATUTS_SIGNES", "FORMULAIRE_UNIQUE");
            case "JOURNAL" -> Set.of("CERTIFICAT_NEGATIF", "STATUTS_SIGNES", "FORMULAIRE_UNIQUE", "ATTESTATION_PARUTION");
            default -> Set.of();
        };
    }

    @Override
    @Transactional
    public void rejectStep(UUID dossierId, AgentRejectRequest request, Utilisateur agent) {
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier introuvable : " + dossierId));
        SuiviEtape currentStep = findCurrentStepForUpdate(dossierId);
        requireClaimOwnership(currentStep, agent);

        clearClaim(currentStep);
        suiviEtapeRepository.save(currentStep);
        workflowEngineService.rejectWorkflowStep(dossier, currentStep.getId(), request.motifRejet(), agent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentHistoryItemDto> getHistory(Utilisateur agent) {
        List<AuditEvent> decisionEvents = auditEventRepository.findByActorIdAndActionInOrderByOccurredAtDesc(
                agent.getId(), List.of(AuditEventAction.ETAPE_VALIDEE, AuditEventAction.ETAPE_REJETEE));
        Map<String, AgentHistoryItemDto> historyItems = new LinkedHashMap<>();
        Set<String> auditedDecisionKeys = new HashSet<>();
        for (AuditEvent event : decisionEvents) {
            if (event.getStepTrackingId() == null) continue;
            suiviEtapeRepository.findById(event.getStepTrackingId()).ifPresent(step -> {
                StepStatus decision = event.getAction() == AuditEventAction.ETAPE_VALIDEE
                        ? StepStatus.VALIDEE : StepStatus.REJETEE;
                auditedDecisionKeys.add(step.getId() + ":" + decision);
                historyItems.put(event.getId().toString(), new AgentHistoryItemDto(
                        step.getDossier().getId(),
                        step.getDossier().getReference(),
                        step.getDossier().getNomEntreprise(),
                        step.getEtapeCode(),
                        step.getLibelle(),
                        decision,
                        event.getReason(),
                        event.getOccurredAt(),
                        event.getStepStartedAt() != null ? event.getStepStartedAt() : step.getStartedAt(),
                        step.getIndicativeDurationDays() != null ? step.getIndicativeDurationDays() : 2
                ));
            });
        }

        List<SuiviEtape> etapesDecidees = suiviEtapeRepository.findHistoryByAgentIdWithDossier(
                agent.getId(),
                List.of(StepStatus.VALIDEE, StepStatus.REJETEE)
        );

        for (SuiviEtape step : etapesDecidees) {
            String key = step.getId() + ":" + step.getStatut();
            if (auditedDecisionKeys.contains(key)) continue;
            historyItems.putIfAbsent(key, new AgentHistoryItemDto(
                    step.getDossier().getId(),
                    step.getDossier().getReference(),
                    step.getDossier().getNomEntreprise(),
                    step.getEtapeCode(),
                    step.getLibelle(),
                    step.getStatut(),
                    step.getMotifRejet(),
                    step.getDecidedAt(),
                    step.getStartedAt(),
                    step.getIndicativeDurationDays() != null ? step.getIndicativeDurationDays() : 2
            ));
        }
        return historyItems.values().stream()
                .sorted(Comparator.comparing(AgentHistoryItemDto::decidedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<UUID> historicalDecisionStepIds(UUID dossierId, Utilisateur agent) {
        List<UUID> auditedStepIds = auditEventRepository
                .findByDossierIdAndActorIdAndActionInOrderByOccurredAtDesc(
                        dossierId, agent.getId(), List.of(AuditEventAction.ETAPE_VALIDEE, AuditEventAction.ETAPE_REJETEE))
                .stream()
                .map(AuditEvent::getStepTrackingId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!auditedStepIds.isEmpty()) return auditedStepIds;
        return suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossierId).stream()
                .filter(step -> step.getAgent() != null && step.getAgent().getId().equals(agent.getId())
                        && (step.getStatut() == StepStatus.VALIDEE || step.getStatut() == StepStatus.REJETEE))
                .map(SuiviEtape::getId)
                .toList();
    }

    private SuiviEtape historicalStepFromAudit(UUID dossierId, Utilisateur agent) {
        UUID stepId = auditEventRepository
                .findByDossierIdAndActorIdAndActionInOrderByOccurredAtDesc(
                        dossierId, agent.getId(), List.of(AuditEventAction.ETAPE_VALIDEE, AuditEventAction.ETAPE_REJETEE))
                .stream()
                .map(AuditEvent::getStepTrackingId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new UnauthorizedDossierAccessException("Aucune décision historique accessible."));
        return suiviEtapeRepository.findById(stepId)
                .orElseThrow(() -> new InvalidDossierStateException("Étape historique introuvable pour journaliser la consultation."));
    }

    private SuiviEtape findCurrentStepForUpdate(UUID dossierId) {
        return suiviEtapeRepository.findFirstByDossierIdAndStatutOrderByOrdreAsc(dossierId, StepStatus.EN_COURS)
                .orElseThrow(() -> new InvalidDossierStateException(
                        "Aucune étape agent en cours à prendre en charge ou traiter pour ce dossier."));
    }

    private void requireCurrentStepForAgent(SuiviEtape step, Utilisateur agent) {
        String agentOrganisme = resolveOrganismeCode(agent);
        if (!isCurrentStepForOrganisme(step, agentOrganisme)) {
            throw new UnauthorizedDossierAccessException(
                    "Accès refusé : l'étape courante ne relève pas de votre organisme (RG5).");
        }
    }

    private void requireClaimOwnership(SuiviEtape step, Utilisateur agent) {
        requireCurrentStepForAgent(step, agent);
        Utilisateur claimant = step.getClaimedByAgent();
        if (claimant == null) {
            throw new AgentClaimConflictException("Prenez en charge ce dossier avant de rendre une décision.");
        }
        if (!claimant.getId().equals(agent.getId())) {
            throw new AgentClaimConflictException("Ce dossier est pris en charge par un autre agent.");
        }
    }

    private boolean isCurrentStepForOrganisme(SuiviEtape step, String agentOrganisme) {
        return step.getActionOwner() == ActorType.AGENT
                && matchesOrganisme(agentOrganisme, step.getOrganismeCode());
    }

    private AgentQueueItemDto toQueueItem(SuiviEtape step, Utilisateur agent) {
        Dossier dossier = step.getDossier();
        Instant startedAt = step.getStartedAt() != null ? step.getStartedAt() : dossier.getCreatedAt();
        int durationDays = step.getIndicativeDurationDays() != null ? step.getIndicativeDurationDays() : 2;
        Instant deadlineAt = startedAt != null ? startedAt.plus(Duration.ofDays(durationDays)) : null;

        String entrepreneurNom = null;
        String entrepreneurEmail = null;
        if (dossier.getEntrepreneur() != null) {
            entrepreneurNom = ((dossier.getEntrepreneur().getPrenom() == null ? "" : dossier.getEntrepreneur().getPrenom())
                    + " " + (dossier.getEntrepreneur().getNom() == null ? "" : dossier.getEntrepreneur().getNom())).trim();
            entrepreneurEmail = dossier.getEntrepreneur().getEmail();
        }

        Utilisateur claimant = step.getClaimedByAgent();
        String claimantName = claimant == null ? null : claimant.getPrenom() + " " + claimant.getNom();
        return new AgentQueueItemDto(
                dossier.getId(),
                dossier.getReference(),
                dossier.getNomEntreprise(),
                dossier.getFormeJuridique(),
                dossier.getStatut(),
                step.getEtapeCode(),
                step.getLibelle(),
                step.getOrganismeCode(),
                startedAt,
                dossier.getSubmittedAt() != null ? dossier.getSubmittedAt() : dossier.getCreatedAt(),
                entrepreneurNom,
                entrepreneurEmail,
                durationDays,
                step.getMotifRejet() != null && !step.getMotifRejet().isBlank(),
                dossier.getRegion(),
                deadlineAt,
                step.getMotifRejet(),
                claimant == null ? null : claimant.getId(),
                claimantName,
                step.getClaimedAt(),
                claimant != null && claimant.getId().equals(agent.getId())
        );
    }

    private String resolveOrganismeCode(Utilisateur agent) {
        if (agent == null) {
            throw new UnauthorizedDossierAccessException("Agent non authentifié.");
        }
        String organization = normalizeOrganismeCode(agent.getOrganisme());
        if (!organization.isBlank()) {
            return organization;
        }
        throw new UnauthorizedDossierAccessException(
                "Accès refusé : aucun organisme n'est associé au compte agent (RG5).");
    }

    private String normalizeOrganismeCode(String organisme) {
        if (organisme == null || organisme.isBlank()) {
            return "";
        }
        String upper = organisme.toUpperCase().trim();
        if (upper.contains("OMPIC") || upper.contains("PROPRIET") || upper.contains("INDUSTRIEL")) return "OMPIC";
        if (upper.contains("CRI") || upper.contains("INVESTISSEMENT")) return "CRI";
        if (upper.contains("TRIBUNAL") || upper.contains("COMMERCE") || upper.contains("JUSTICE")) return "TRIBUNAL";
        if (upper.contains("DGI") || upper.contains("IMPOT") || upper.contains("FISC")) return "DGI";
        if (upper.contains("CNSS") || upper.contains("SECURITE")) return "CNSS";
        if (upper.contains("JOURNAL") || upper.contains("ANNONCE")) return "JOURNAL";
        if (upper.contains("GUICHET")) return "GUICHET";
        return upper;
    }

    private boolean matchesOrganisme(String agentOrganisme, String stepOrganisme) {
        if (agentOrganisme == null || agentOrganisme.isBlank()
                || stepOrganisme == null || stepOrganisme.isBlank()) {
            return false;
        }
        return agentOrganisme.equalsIgnoreCase(normalizeOrganismeCode(stepOrganisme));
    }

    private void clearClaim(SuiviEtape step) {
        step.setClaimedByAgent(null);
        step.setClaimedAt(null);
    }

    private String writeJson(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Impossible d'enregistrer le résultat de validation.", exception);
        }
    }
}
