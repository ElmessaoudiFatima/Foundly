package com.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corps de la requête POST /admin/organizations.
 * Le code sera normalisé (trim + toUpperCase) dans le service avant validation du motif.
 */
public record OrganizationCreateRequest(

        @NotBlank(message = "Le nom de l'organisme est obligatoire")
        @Size(max = 150, message = "Le nom ne peut pas dépasser 150 caractères")
        String name,

        /**
         * Sigle de l'organisme — normalisé en majuscules par le service.
         * Motif : lettre majuscule, puis 1 à 19 caractères alphanumériques ou '_'.
         */
        @NotBlank(message = "Le code (sigle) de l'organisme est obligatoire")
        @Pattern(
            regexp = "^[A-Z][A-Z0-9_]{1,19}$",
            message = "Le code doit commencer par une lettre majuscule et contenir de 2 à 20 caractères alphanumériques ou '_'"
        )
        String code,

        @NotBlank(message = "La description est obligatoire")
        @Size(max = 500, message = "La description ne peut pas dépasser 500 caractères")
        String description
) {}
