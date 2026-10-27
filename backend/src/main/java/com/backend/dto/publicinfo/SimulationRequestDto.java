package com.backend.dto.publicinfo;

import com.backend.enums.LegalFormCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record SimulationRequestDto(
        @NotNull(message = "La forme juridique est obligatoire.")
        LegalFormCode legalFormCode,

        @PositiveOrZero(message = "Le montant du capital doit être positif ou nul.")
        BigDecimal capitalAmountMad
) {}
