package com.backend.dto.agent;

import com.backend.enums.DocumentReviewStatus;
import jakarta.validation.constraints.Size;

public record AgentDocumentReviewRequest(
        DocumentReviewStatus status,
        @Size(max = 1000, message = "Le commentaire ne peut pas dépasser 1000 caractères.")
        String comment
) {}
