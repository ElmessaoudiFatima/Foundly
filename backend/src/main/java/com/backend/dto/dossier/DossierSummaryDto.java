package com.backend.dto.dossier;

import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DossierSummaryDto(
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
        Instant updatedAt
) {}
