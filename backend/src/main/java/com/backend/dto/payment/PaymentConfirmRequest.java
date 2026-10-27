package com.backend.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PaymentConfirmRequest(
        @NotBlank(message = "Le numéro de carte est obligatoire.")
        String cardNumber,

        @NotBlank(message = "Le nom du titulaire de la carte est obligatoire.")
        String cardHolder,

        @NotBlank(message = "La date d'expiration est obligatoire.")
        @Pattern(regexp = "^(0[1-9]|1[0-2])\\/([0-9]{2})$", message = "Format d'expiration invalide (MM/AA).")
        String expirationDate,

        @NotBlank(message = "Le code CVV est obligatoire.")
        @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV invalide (3 ou 4 chiffres).")
        String cvv
) {}
