package com.backend.dto.publicinfo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record AssistantRequestDto(
        @NotNull(message = "Le nombre d'associés est requis.")
        @Min(value = 1, message = "Il faut au minimum 1 associé.")
        Integer numberOfPartners,

        @NotNull(message = "Le choix de la responsabilité est requis.")
        Boolean wantsLimitedLiability,

        @PositiveOrZero(message = "Le capital estimé doit être positif ou nul.")
        BigDecimal estimatedCapitalMad,

        Boolean intendsToHireEmployees,

        String activityDescription
) {}
