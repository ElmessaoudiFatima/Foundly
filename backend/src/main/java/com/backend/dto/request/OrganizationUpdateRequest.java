package com.backend.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Corps de la requête PATCH /admin/organizations/{organizationCode}.
 * Au moins un des deux champs doit être non null (vérifié dans le service).
 * Le code n'est jamais modifiable.
 */
public record OrganizationUpdateRequest(

        @Size(max = 150, message = "Le nom ne peut pas dépasser 150 caractères")
        String name,

        @Size(max = 500, message = "La description ne peut pas dépasser 500 caractères")
        String description
) {}
