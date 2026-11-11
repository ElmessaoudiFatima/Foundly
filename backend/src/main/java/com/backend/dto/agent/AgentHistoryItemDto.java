package com.backend.dto.agent;

import com.backend.enums.StepStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Représente une entrée dans l'historique des dossiers traités par l'agent.
 * Réf. CDC § 8.2 — Historique des dossiers traités.
 */
public record AgentHistoryItemDto(
        UUID dossierId,
        String dossierReference,
        String nomEntreprise,
        String etapeCode,
        String etapeLibelle,
        StepStatus decisionPrise,   // VALIDEE ou REJETEE
        String motifRejet,
        Instant decidedAt,
        Instant startedAt,
        Integer indicativeDurationDays
) {
    public AgentHistoryItemDto(
            UUID dossierId,
            String dossierReference,
            String nomEntreprise,
            String etapeCode,
            String etapeLibelle,
            StepStatus decisionPrise,
            String motifRejet,
            Instant decidedAt
    ) {
        this(dossierId, dossierReference, nomEntreprise, etapeCode, etapeLibelle,
             decisionPrise, motifRejet, decidedAt, null, 2);
    }
}
