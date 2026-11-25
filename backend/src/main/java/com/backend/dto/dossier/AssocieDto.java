package com.backend.dto.dossier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record AssocieDto(
        UUID id,

        @NotBlank(message = "Le nom de l'associé est obligatoire.")
        @Size(max = 100, message = "Le nom ne peut excéder 100 caractères.")
        String nom,

        @NotBlank(message = "Le prénom de l'associé est obligatoire.")
        @Size(max = 100, message = "Le prénom ne peut excéder 100 caractères.")
        String prenom,

        @NotBlank(message = "Le CIN ou Passeport de l'associé est obligatoire.")
        @Size(max = 30, message = "Le CIN ou Passeport ne peut excéder 30 caractères.")
        String cinOuPasseport,

        @NotBlank(message = "La nationalité est obligatoire.")
        @Size(max = 50, message = "La nationalité ne peut excéder 50 caractères.")
        String nationalite,

        @NotBlank(message = "L'adresse est obligatoire.")
        @Size(max = 255, message = "L'adresse ne peut excéder 255 caractères.")
        String adresse,

        @NotNull(message = "Le nombre de parts est obligatoire.")
        @Positive(message = "Le nombre de parts doit être supérieur à zéro.")
        Long nombreParts,

        BigDecimal montantApport,

        BigDecimal pourcentageDetention,

        Boolean actif
) {
    public AssocieDto(UUID id, String nom, String prenom, String cinOuPasseport,
                      String nationalite, String adresse, Long nombreParts,
                      BigDecimal montantApport, BigDecimal pourcentageDetention) {
        this(id, nom, prenom, cinOuPasseport, nationalite, adresse, nombreParts,
                montantApport, pourcentageDetention, true);
    }
}
