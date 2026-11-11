package com.backend.dto.agent;

import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Représente un dossier dans la file d'attente de l'agent.
 * Réf. CDC § 8.2 — File d'attente des dossiers à traiter.
 */
public record AgentQueueItemDto(
        UUID dossierId,
        String dossierReference,
        String nomEntreprise,
        LegalFormCode formeJuridique,
        DossierStatus statutDossier,
        String etapeCode,
        String etapeLibelle,
        String organismeCode,
        Instant etapeStartedAt,
        Instant dossierSubmittedAt,
        String entrepreneurNom,
        String entrepreneurEmail,
        Integer indicativeDurationDays,
        Boolean isResubmitted,
        String region,
        Instant deadlineAt,
        String previousRejectionReason,
        UUID claimedByAgentId,
        String claimedByAgentName,
        Instant claimedAt,
        Boolean claimedByCurrentAgent
) {
    public AgentQueueItemDto(
            UUID dossierId,
            String dossierReference,
            String nomEntreprise,
            LegalFormCode formeJuridique,
            DossierStatus statutDossier,
            String etapeCode,
            String etapeLibelle,
            String organismeCode,
            Instant etapeStartedAt,
            Instant dossierSubmittedAt,
            String entrepreneurNom,
            String entrepreneurEmail
    ) {
        this(dossierId, dossierReference, nomEntreprise, formeJuridique, statutDossier,
             etapeCode, etapeLibelle, organismeCode, etapeStartedAt, dossierSubmittedAt,
             entrepreneurNom, entrepreneurEmail, 2, false, null, null, null, null, null, null, false);
    }
}
