package com.backend.dto.response;

import java.util.List;

/**
 * DTO de réponse pour un organisme — aligné sur le schéma OpenAPI Organization.
 *
 * @param code             sigle unique de l'organisme
 * @param name             nom complet
 * @param description      description du rôle dans le circuit de création
 * @param activeAgentCount nombre d'agents de rôle AGENT avec actif=true rattachés à cet organisme
 * @param managedSteps     libellés des étapes du workflow actif (liste vide : entités Workflow
 *                         non encore implémentées dans le projet)
 */
public record OrganizationResponse(
        String code,
        String name,
        String description,
        long activeAgentCount,
        List<String> managedSteps
) {}
