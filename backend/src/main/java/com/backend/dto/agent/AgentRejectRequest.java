package com.backend.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requête de rejet d'une étape avec motif obligatoire.
 * Réf. CDC § 8.2, RG8 — le motif est obligatoire et doit être suffisamment détaillé.
 */
public record AgentRejectRequest(
        @NotBlank(message = "Le motif de rejet est obligatoire (RG8).")
        @Size(min = 10, max = 500, message = "Le motif doit contenir entre 10 et 500 caractères.")
        String motifRejet
) {}

