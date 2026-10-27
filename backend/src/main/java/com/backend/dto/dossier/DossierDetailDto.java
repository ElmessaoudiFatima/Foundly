package com.backend.dto.dossier;

import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DossierDetailDto(
        UUID id,
        String reference,
        String proposedCompanyName,
        LegalFormCode legalFormCode,
        DossierStatus status,
        String region,
        StepReferenceDto currentStep,
        BigDecimal capitalAmountMad,
        Integer progressionPercentage,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt,
        String activityDescription,
        boolean capitalBlockageRequired,
        String headOfficeAddress,
        Instant submittedAt,
        Instant finalizedAt,
        Double checklistCompletionRate,
        UUID workflowVersionId,
        List<StepTrackingDto> steps
) {}
