package com.backend.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentCreateAdminRequest(
        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 80)
        String firstName,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 80)
        String lastName,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Format d'email invalide")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, max = 72, message = "Le mot de passe doit comporter entre 8 et 72 caractères")
        String password,

        @NotBlank(message = "L'organisme est obligatoire")
        String organization,

        String phone,
        String cin
) {}
