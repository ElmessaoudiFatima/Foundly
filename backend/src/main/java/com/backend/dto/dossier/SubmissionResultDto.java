package com.backend.dto.dossier;

import com.backend.enums.DossierStatus;

import java.time.Instant;
import java.util.UUID;

public record SubmissionResultDto(
        UUID dossierId,
        DossierStatus status,
        StepReferenceDto currentStep,
        Instant submittedAt,
        String message
) {}
