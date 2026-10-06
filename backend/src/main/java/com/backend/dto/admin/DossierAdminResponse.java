package com.backend.dto.admin;

import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;

import java.time.Instant;
import java.util.UUID;

public record DossierAdminResponse(
        UUID id,
        String reference,
        String companyName,
        String entrepreneurName,
        String entrepreneurCinMasked,
        LegalFormCode legalForm,
        String legalFormLabel,
        String region,
        String currentStep,
        DossierStatus status,
        String statusLabel,
        Instant createdAt,
        Instant updatedAt,
        String createdFormatted,
        String updatedFormatted
) {}
