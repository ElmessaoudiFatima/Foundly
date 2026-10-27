package com.backend.service.impl;

import com.backend.dto.payment.PaymentConfirmRequest;
import com.backend.dto.payment.PaymentInitiateRequest;
import com.backend.dto.payment.PaymentResponseDto;
import com.backend.entity.*;
import com.backend.enums.*;
import com.backend.exception.*;
import com.backend.repository.AuditEventRepository;
import com.backend.repository.DossierRepository;
import com.backend.repository.PaiementRepository;
import com.backend.repository.SuiviEtapeRepository;
import com.backend.service.NotificationService;
import com.backend.service.PaymentService;
import com.backend.service.WorkflowEngineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaiementRepository paiementRepository;
    private final DossierRepository dossierRepository;
    private final SuiviEtapeRepository suiviEtapeRepository;
    private final AuditEventRepository auditEventRepository;
    private final NotificationService notificationService;
    private final WorkflowEngineService workflowEngineService;

    public PaymentServiceImpl(
            PaiementRepository paiementRepository,
            DossierRepository dossierRepository,
            SuiviEtapeRepository suiviEtapeRepository,
            AuditEventRepository auditEventRepository,
            NotificationService notificationService,
            WorkflowEngineService workflowEngineService
    ) {
        this.paiementRepository = paiementRepository;
        this.dossierRepository = dossierRepository;
        this.suiviEtapeRepository = suiviEtapeRepository;
        this.auditEventRepository = auditEventRepository;
        this.notificationService = notificationService;
        this.workflowEngineService = workflowEngineService;
    }

    @Override
    @Transactional
    public PaymentResponseDto initiatePayment(UUID dossierId, PaymentInitiateRequest request, Utilisateur currentUser) {
        Dossier dossier = getDossierAndCheckAccess(dossierId, currentUser);

        SuiviEtape step = null;
        BigDecimal amount = request.amountMad();

        if (request.stepTrackingId() != null) {
            step = suiviEtapeRepository.findById(request.stepTrackingId())
                    .orElseThrow(() -> new StepNotFoundException("Étape de workflow introuvable : " + request.stepTrackingId()));
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                amount = step.getFraisMad();
            }
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = new BigDecimal("232.76"); // Frais certificat négatif standard par défaut
        }

        // Vérifier s'il y a déjà un paiement en attente
        if (step != null) {
            Paiement existing = paiementRepository.findFirstByDossierIdAndSuiviEtapeIdAndStatut(
                    dossierId, step.getId(), PaymentStatus.EN_ATTENTE).orElse(null);
            if (existing != null) {
                return mapToDto(existing);
            }
        }

        Paiement paiement = new Paiement();
        paiement.setDossier(dossier);
        paiement.setSuiviEtape(step);
        paiement.setMontantMad(amount);
        paiement.setReferenceSimulation(generateSimulatedReference());
        paiement.setStatut(PaymentStatus.EN_ATTENTE);
        paiement.setMethode("CARTE_BANCAIRE_SIMULEE");
        paiement.setCreatedAt(Instant.now());

        Paiement saved = paiementRepository.save(paiement);

        return mapToDto(saved);
    }

    @Override
    @Transactional
    public PaymentResponseDto confirmPayment(UUID dossierId, UUID paymentId, PaymentConfirmRequest request, Utilisateur currentUser) {
        Dossier dossier = getDossierAndCheckAccess(dossierId, currentUser);

        Paiement paiement = paiementRepository.findByIdAndDossierId(paymentId, dossierId)
                .orElseThrow(() -> new PaymentNotFoundException("Paiement introuvable : " + paymentId));

        if (paiement.getStatut() == PaymentStatus.CONFIRME) {
            return mapToDto(paiement);
        }

        // Masquer le numéro de carte bancaire (ex: **** **** **** 1234)
        String rawCard = request.cardNumber().replaceAll("\\s+", "");
        String last4 = rawCard.length() >= 4 ? rawCard.substring(rawCard.length() - 4) : "0000";
        paiement.setNumeroCarteMasque("**** **** **** " + last4);
        paiement.setStatut(PaymentStatus.CONFIRME);
        paiement.setConfirmedAt(Instant.now());

        Paiement saved = paiementRepository.save(paiement);

        // Audit
        auditEventRepository.save(new AuditEvent(
                dossier,
                AuditEventAction.PAIEMENT_CONFIRME,
                ActorType.ENTREPRENEUR,
                currentUser,
                currentUser.getPrenom() + " " + currentUser.getNom(),
                paiement.getSuiviEtape() != null ? paiement.getSuiviEtape().getId() : null,
                null,
                "Paiement simulé confirmé : " + paiement.getMontantMad() + " MAD (Réf : " + paiement.getReferenceSimulation() + ")"
        ));

        // Notification
        notificationService.sendNotification(
                dossier.getEntrepreneur(),
                dossier,
                "Paiement confirmé",
                "Le règlement simulé de " + paiement.getMontantMad() + " MAD a été validé. Réf : " + paiement.getReferenceSimulation(),
                NotificationChannel.IN_APP,
                NotificationType.PAIEMENT_CONFIRME
        );

        // Si l'étape nécessitait un paiement pour progresser et que le dossier est en cours, faire avancer
        if (dossier.getStatut() == DossierStatus.EN_COURS && paiement.getSuiviEtape() != null) {
            if (paiement.getSuiviEtape().getStatut() == StepStatus.EN_COURS) {
                workflowEngineService.advanceWorkflow(dossier, "Paiement des frais administratifs acquitté");
            }
        }

        return mapToDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getPayments(UUID dossierId, Utilisateur currentUser) {
        getDossierAndCheckAccess(dossierId, currentUser);
        return paiementRepository.findByDossierIdOrderByCreatedAtDesc(dossierId).stream()
                .map(this::mapToDto)
                .toList();
    }

    private Dossier getDossierAndCheckAccess(UUID dossierId, Utilisateur user) {
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier introuvable : " + dossierId));

        if (user.getRole() == RoleUtilisateur.ADMINISTRATEUR) {
            return dossier;
        }

        if (user.getRole() == RoleUtilisateur.ENTREPRENEUR && !dossier.getEntrepreneur().getId().equals(user.getId())) {
            throw new UnauthorizedDossierAccessException("Accès refusé : vous n'êtes pas le propriétaire de ce dossier (RG13).");
        }

        return dossier;
    }

    private String generateSimulatedReference() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("SIM-FND-");
        Random r = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private PaymentResponseDto mapToDto(Paiement p) {
        String stepLabel = p.getSuiviEtape() != null ? p.getSuiviEtape().getLibelle() : "Frais administratifs";
        UUID stepId = p.getSuiviEtape() != null ? p.getSuiviEtape().getId() : null;

        return new PaymentResponseDto(
                p.getId(),
                p.getDossier().getId(),
                stepId,
                stepLabel,
                p.getMontantMad(),
                p.getStatut(),
                p.getReferenceSimulation(),
                p.getMethode(),
                p.getNumeroCarteMasque(),
                p.getCreatedAt(),
                p.getConfirmedAt()
        );
    }
}
