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
        List<StepTrackingDto> steps,
        String entrepreneurNom,
        String entrepreneurEmail,
        String entrepreneurCin,
        String entrepreneurTelephone,
        BigDecimal valeurNominalePart,
        Long nombreTotalParts,
        List<AssocieDto> associes,
        List<GerantDto> gerants
) {
    public DossierDetailDto(
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
            List<StepTrackingDto> steps,
            String entrepreneurNom,
            String entrepreneurEmail,
            String entrepreneurCin,
            String entrepreneurTelephone
    ) {
        this(id, reference, proposedCompanyName, legalFormCode, status, region, currentStep,
                capitalAmountMad, progressionPercentage, rejectionReason, createdAt, updatedAt,
                activityDescription, capitalBlockageRequired, headOfficeAddress, submittedAt,
                finalizedAt, checklistCompletionRate, workflowVersionId, steps,
                entrepreneurNom, entrepreneurEmail, entrepreneurCin, entrepreneurTelephone,
                null, null, List.of(), List.of());
    }

    // Getters alias pour compatibilité totale avec les interfaces frontend
    public String getNomEntreprise() { return proposedCompanyName; }
    public LegalFormCode getFormeJuridique() { return legalFormCode; }
    public DossierStatus getStatut() { return status; }
    public BigDecimal getCapitalMad() { return capitalAmountMad; }
    public String getDescriptionActivite() { return activityDescription; }
    public String getAdresseSiege() { return headOfficeAddress; }
}
