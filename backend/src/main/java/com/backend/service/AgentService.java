package com.backend.service;

import com.backend.dto.agent.AgentHistoryItemDto;
import com.backend.dto.agent.AgentDocumentReviewRequest;
import com.backend.dto.agent.AgentQueueItemDto;
import com.backend.dto.agent.AgentRejectRequest;
import com.backend.dto.agent.AgentValidateRequest;
import com.backend.dto.audit.PagedAuditEventDto;
import com.backend.entity.Utilisateur;

import java.util.List;
import java.util.UUID;

/**
 * Service dédié à l'espace agent administratif.
 * Réf. CDC § 8.2 — Agents de chaque organisme (OMPIC, CRI, TRIBUNAL, DGI, CNSS, JOURNAL).
 */
public interface AgentService {

    /**
     * File d'attente : dossiers EN_COURS dont l'étape courante
     * appartient à l'organisme de l'agent connecté et attend une décision d'agent.
     */
    List<AgentQueueItemDto> getQueue(Utilisateur agent);

    AgentQueueItemDto takeInCharge(UUID dossierId, Utilisateur agent);

    void releaseClaim(UUID dossierId, Utilisateur agent);

    void assertDossierAccess(UUID dossierId, Utilisateur agent);

    void assertClaimedByAgent(UUID dossierId, Utilisateur agent);

    void assertDocumentAccess(UUID dossierId, UUID documentId, Utilisateur agent);

    boolean isDocumentTypeAllowedForAgent(String documentTypeCode, Utilisateur agent);

    void assertHistoricalDossierAccess(UUID dossierId, Utilisateur agent);

    void assertHistoricalDocumentAccess(UUID dossierId, UUID documentId, Utilisateur agent);

    PagedAuditEventDto getHistoricalAuditEvents(UUID dossierId, int page, int size, Utilisateur agent);

    void reviewDocument(UUID dossierId, UUID documentId, AgentDocumentReviewRequest request, Utilisateur agent);

    void auditDocumentConsultation(UUID dossierId, UUID documentId, Utilisateur agent);

    void auditHistoricalDocumentConsultation(UUID dossierId, UUID documentId, Utilisateur agent);

    /**
     * Valider l'étape EN_COURS d'un dossier.
     * Déclenche le passage automatique à l'étape suivante (RG7).
     */
    void validateStep(UUID dossierId, AgentValidateRequest request, Utilisateur agent);

    /**
     * Rejeter l'étape EN_COURS d'un dossier avec motif obligatoire (RG8).
     * Passe le dossier au statut À corriger et notifie l'entrepreneur.
     */
    void rejectStep(UUID dossierId, AgentRejectRequest request, Utilisateur agent);

    /**
     * Historique des étapes traitées (VALIDEE ou REJETEE) par cet agent.
     */
    List<AgentHistoryItemDto> getHistory(Utilisateur agent);
}
