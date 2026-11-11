package com.backend.dto.agent;

import java.util.Map;

/**
 * Requête de validation d'une étape par un agent.
 * La checklist et les résultats métier sont enregistrés avec la décision.
 * Réf. CDC § 8.2, RG7.
 */
public record AgentValidateRequest(
        String notes,
        Map<String, Boolean> checklist,
        Map<String, String> stepResult
) {}
