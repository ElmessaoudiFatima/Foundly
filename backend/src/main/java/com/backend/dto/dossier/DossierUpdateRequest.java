package com.backend.dto.dossier;

import com.backend.enums.LegalFormCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record DossierUpdateRequest(
        LegalFormCode legalFormCode,

        @Size(max = 200, message = "Le nom de l'entreprise ne peut excéder 200 caractères.")
        String proposedCompanyName,

        @Size(min = 2, max = 100, message = "La région doit comporter entre 2 et 100 caractères.")
        String region,

        @Size(max = 1000, message = "La description d'activité ne peut excéder 1000 caractères.")
        String activityDescription,

        @PositiveOrZero(message = "Le montant du capital doit être positif ou nul.")
        BigDecimal capitalAmountMad,

        @Size(max = 500, message = "L'adresse du siège ne peut excéder 500 caractères.")
        String headOfficeAddress,

        BigDecimal valeurNominalePart,

        List<@Valid AssocieDto> associes,

        List<@Valid GerantDto> gerants
) {
    public DossierUpdateRequest(
            LegalFormCode legalFormCode,
            String proposedCompanyName,
            String region,
            String activityDescription,
            BigDecimal capitalAmountMad,
            String headOfficeAddress
    ) {
        this(legalFormCode, proposedCompanyName, region, activityDescription, capitalAmountMad, headOfficeAddress, null, null, null);
    }
}
