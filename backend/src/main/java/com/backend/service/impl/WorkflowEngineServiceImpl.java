package com.backend.service.impl;

import com.backend.entity.*;
import com.backend.enums.*;
import com.backend.repository.AuditEventRepository;
import com.backend.repository.DocumentRepository;
import com.backend.repository.DossierRepository;
import com.backend.repository.SuiviEtapeRepository;
import com.backend.service.NotificationService;
import com.backend.service.WorkflowEngineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowEngineServiceImpl implements WorkflowEngineService {

    private final SuiviEtapeRepository suiviEtapeRepository;
    private final DossierRepository dossierRepository;
    private final AuditEventRepository auditEventRepository;
    private final DocumentRepository documentRepository;
    private final NotificationService notificationService;

    public WorkflowEngineServiceImpl(
            SuiviEtapeRepository suiviEtapeRepository,
            DossierRepository dossierRepository,
            AuditEventRepository auditEventRepository,
            DocumentRepository documentRepository,
            NotificationService notificationService
    ) {
        this.suiviEtapeRepository = suiviEtapeRepository;
        this.dossierRepository = dossierRepository;
        this.auditEventRepository = auditEventRepository;
        this.documentRepository = documentRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public List<SuiviEtape> initializeStepsForDossier(Dossier dossier) {
        List<SuiviEtape> steps = new ArrayList<>();

        if (dossier.getFormeJuridique() == LegalFormCode.AUTO_ENTREPRENEUR) {
            steps.add(createStep(dossier, "VERIFICATION_ELIGIBILITE", "Vérification de l'éligibilité au statut Auto-Entrepreneur", 1, ActorType.AGENT, "CRI", BigDecimal.ZERO, false, 1));
            steps.add(createStep(dossier, "FORMULAIRE_INSCRIPTION", "Dépôt du formulaire RNAE et justificatifs", 2, ActorType.ENTREPRENEUR, null, BigDecimal.ZERO, false, 1));
            steps.add(createStep(dossier, "VALIDATION_POSTE_MAROC", "Validation d'identité en agence et conformité", 3, ActorType.AGENT, "CRI", BigDecimal.ZERO, false, 2));
            steps.add(createStep(dossier, "DELIVRANCE_CARTE", "Attribution identifiant fiscal et délivrance de la carte", 4, ActorType.AGENT, "DGI", BigDecimal.ZERO, false, 2));
        } else {
            // SARL, SARL_AU, SA
            steps.add(createStep(dossier, "CERTIFICAT_NEGATIF", "Réservation du nom / Certificat négatif (OMPIC)", 1, ActorType.AGENT, "OMPIC", new BigDecimal("232.76"), true, 2));
            steps.add(createStep(dossier, "REDACTION_STATUTS", "Rédaction, signature et légalisation des statuts", 2, ActorType.ENTREPRENEUR, "CRI", BigDecimal.ZERO, false, 1));

            // Étape 3 : Blocage du capital social (RG15)
            SuiviEtape step3 = createStep(dossier, "BLOCAGE_CAPITAL", "Blocage du capital social (Banque externe)", 3, ActorType.ENTREPRENEUR, null, BigDecimal.ZERO, false, 2);
            if (!dossier.isCapitalBlockageRequired()) {
                // Dispense légale pour SARL / SARL_AU <= 100 000 MAD
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

        dossier.setCurrentStepCode(firstStep.getEtapeCode());
        dossier.setCurrentStepOrder(firstStep.getOrdre());
        dossier.setEtapeActuelle(firstStep.getLibelle());

        return suiviEtapeRepository.saveAll(steps);
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

    @Override
    @Transactional
    public void advanceWorkflow(Dossier dossier, String notes) {
        advanceWorkflow(dossier, notes, null);
    }

    @Override
    @Transactional
    public void advanceWorkflow(Dossier dossier, String notes, Utilisateur decidingAgent) {
        List<SuiviEtape> steps = suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossier.getId());

        SuiviEtape currentStep = steps.stream()
                .filter(s -> s.getStatut() == StepStatus.EN_COURS)
                .findFirst()
                .orElse(null);

        if (currentStep != null) {
            currentStep.setStatut(StepStatus.VALIDEE);
            currentStep.setDecidedAt(Instant.now());
            suiviEtapeRepository.save(currentStep);

            // Audit
            String actorName = decidingAgent == null
                    ? "Système Foundly"
                    : decidingAgent.getPrenom() + " " + decidingAgent.getNom();
            AuditEvent decisionEvent = new AuditEvent(
                    dossier,
                    AuditEventAction.ETAPE_VALIDEE,
                    decidingAgent == null ? ActorType.SYSTEME : ActorType.AGENT,
                    decidingAgent,
                    actorName,
                    currentStep.getId(),
                    null,
                    "Étape validée : " + currentStep.getLibelle() + (notes != null ? " (" + notes + ")" : "")
            );
            decisionEvent.setStepStartedAt(currentStep.getStartedAt());
            auditEventRepository.save(decisionEvent);
        }

        // Trouver la prochaine étape en attente
        SuiviEtape nextStep = steps.stream()
                .filter(s -> s.getStatut() == StepStatus.EN_ATTENTE)
                .min(Comparator.comparingInt(SuiviEtape::getOrdre))
                .orElse(null);

        if (nextStep != null) {
            nextStep.setStatut(StepStatus.EN_COURS);
            nextStep.setStartedAt(Instant.now());
            suiviEtapeRepository.save(nextStep);

            dossier.setCurrentStepOrder(nextStep.getOrdre());
            dossier.setCurrentStepCode(nextStep.getEtapeCode());
            dossier.setEtapeActuelle(nextStep.getLibelle());
            dossier.setStatut(DossierStatus.EN_COURS);
            dossierRepository.save(dossier);

            notificationService.sendNotification(
                    dossier.getEntrepreneur(),
                    dossier,
                    "Étape validée : " + (currentStep != null ? currentStep.getLibelle() : ""),
                    "Votre dossier avance ! Nouvelle étape en cours : " + nextStep.getLibelle(),
                    NotificationChannel.IN_APP,
                    NotificationType.ETAPE_VALIDEE
            );
        } else {
            // Toutes les étapes sont validées -> Dossier finalisé
            dossier.setStatut(DossierStatus.FINALISE);
            dossier.setFinalizedAt(Instant.now());
            dossier.setEtapeActuelle("Procédure finalisée — Entreprise immatriculée");
            dossierRepository.save(dossier);

            auditEventRepository.save(new AuditEvent(
                    dossier,
                    AuditEventAction.DOSSIER_FINALISE,
                    ActorType.SYSTEME,
                    null,
                    "Système Foundly",
                    null,
                    null,
                    "Toutes les formalités administratives ont été validées avec succès."
            ));

            notificationService.sendNotification(
                    dossier.getEntrepreneur(),
                    dossier,
                    "Félicitations ! Dossier finalisé",
                    "Votre entreprise " + dossier.getNomEntreprise() + " est officiellement immatriculée au Maroc.",
                    NotificationChannel.IN_APP,
                    NotificationType.DOSSIER_FINALISE
            );
        }
    }

    @Override
    @Transactional
    public void rejectWorkflowStep(Dossier dossier, UUID stepTrackingId, String motifRejet, Utilisateur agent) {
        SuiviEtape step = suiviEtapeRepository.findById(stepTrackingId)
                .orElseThrow(() -> new IllegalArgumentException("Étape introuvable : " + stepTrackingId));

        step.setStatut(StepStatus.REJETEE);
        step.setMotifRejet(motifRejet);
        step.setDecidedAt(Instant.now());
        if (agent != null) {
            step.setAgent(agent);
        }
        suiviEtapeRepository.save(step);

        dossier.setStatut(DossierStatus.A_CORRIGER);
        dossier.setRejectionReason(motifRejet);
        dossierRepository.save(dossier);

        String agentNom = agent != null ? agent.getPrenom() + " " + agent.getNom() : "Agent administratif";

        AuditEvent decisionEvent = new AuditEvent(
                dossier,
                AuditEventAction.ETAPE_REJETEE,
                ActorType.AGENT,
                agent,
                agentNom,
                step.getId(),
                motifRejet,
                "L'étape " + step.getLibelle() + " a été rejetée avec motif : " + motifRejet
        );
        decisionEvent.setStepStartedAt(step.getStartedAt());
        auditEventRepository.save(decisionEvent);

        notificationService.sendNotification(
                dossier.getEntrepreneur(),
                dossier,
                "Action requise : Dossier à corriger",
                "L'étape " + step.getLibelle() + " nécessite des corrections : « " + motifRejet + " ». Veuillez modifier vos documents et soumettre à nouveau.",
                NotificationChannel.IN_APP,
                NotificationType.ETAPE_REJETEE
        );
    }

    @Override
    @Transactional
    public void resumeWorkflowAfterCorrection(Dossier dossier) {
        List<SuiviEtape> steps = suiviEtapeRepository.findByDossierIdOrderByOrdreAsc(dossier.getId());

        SuiviEtape rejectedStep = steps.stream()
                .filter(s -> s.getStatut() == StepStatus.REJETEE)
                .findFirst()
                .orElse(null);

        if (rejectedStep != null) {
            rejectedStep.setStatut(StepStatus.EN_COURS);
            rejectedStep.setStartedAt(Instant.now());
            rejectedStep.setDecidedAt(null);
            suiviEtapeRepository.save(rejectedStep);

            dossier.setCurrentStepOrder(rejectedStep.getOrdre());
            dossier.setCurrentStepCode(rejectedStep.getEtapeCode());
            dossier.setEtapeActuelle(rejectedStep.getLibelle());
        }

        dossier.setStatut(DossierStatus.EN_COURS);
        dossier.setRejectionReason(null);
        dossierRepository.save(dossier);

        auditEventRepository.save(new AuditEvent(
                dossier,
                AuditEventAction.DOSSIER_CORRIGE,
                ActorType.ENTREPRENEUR,
                dossier.getEntrepreneur(),
                dossier.getEntrepreneur().getPrenom() + " " + dossier.getEntrepreneur().getNom(),
                rejectedStep != null ? rejectedStep.getId() : null,
                null,
                "Dossier corrigé et re-soumis par l'entrepreneur."
        ));

        notificationService.sendNotification(
                dossier.getEntrepreneur(),
                dossier,
                "Corrections envoyées",
                "Votre dossier a été re-soumis avec succès. Le traitement reprend à l'étape en cours.",
                NotificationChannel.IN_APP,
                NotificationType.DOSSIER_SOUMIS
        );
    }

    @Override
    @Transactional
    public void checkAndAutoValidateCapitalBlockageIfUploaded(Dossier dossier) {
        if (!dossier.isCapitalBlockageRequired()) {
            return;
        }

        boolean hasAttestation = documentRepository.existsByDossierIdAndDocumentTypeCode(
                dossier.getId(), "ATTESTATION_BLOCAGE_CAPITAL");

        if (hasAttestation) {
            suiviEtapeRepository.findByDossierIdAndEtapeCode(dossier.getId(), "BLOCAGE_CAPITAL")
                    .ifPresent(step3 -> {
                        if (step3.getStatut() == StepStatus.EN_COURS || step3.getStatut() == StepStatus.EN_ATTENTE) {
                            step3.setStatut(StepStatus.VALIDEE);
                            step3.setDecidedAt(Instant.now());
                            suiviEtapeRepository.save(step3);

                            auditEventRepository.save(new AuditEvent(
                                    dossier,
                                    AuditEventAction.ETAPE_VALIDEE,
                                    ActorType.SYSTEME,
                                    null,
                                    "Système Foundly (RG15)",
                                    step3.getId(),
                                    null,
                                    "Attestation bancaire de blocage du capital téléversée. Étape 3 validée automatiquement."
                            ));
                        }
                    });
        }
    }
}
