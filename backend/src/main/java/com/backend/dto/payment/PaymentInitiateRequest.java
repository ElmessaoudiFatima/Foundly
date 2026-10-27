package com.backend.dto.payment;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentInitiateRequest(
        @NotNull(message = "L'identifiant du suivi d'étape est requis.")
        UUID stepTrackingId,

        BigDecimal amountMad
) {}
