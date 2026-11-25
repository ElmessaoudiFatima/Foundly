package com.backend.dto.dossier;

import com.backend.enums.ModeNominationGerant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record GerantDto(
        UUID id,

        UUID associeId,

        boolean estAssocie,

        @NotBlank(message = "Le nom du gérant est obligatoire.")
        @Size(max = 100, message = "Le nom ne peut excéder 100 caractères.")
        String nom,

        @NotBlank(message = "Le prénom du gérant est obligatoire.")
        @Size(max = 100, message = "Le prénom ne peut excéder 100 caractères.")
        String prenom,

        @NotBlank(message = "Le CIN ou Passeport du gérant est obligatoire.")
        @Size(max = 30, message = "Le CIN ou Passeport ne peut excéder 30 caractères.")
        String cinOuPasseport,

        @NotBlank(message = "La nationalité du gérant est obligatoire.")
        @Size(max = 50, message = "La nationalité ne peut excéder 50 caractères.")
        String nationalite,

        @NotBlank(message = "L'adresse du gérant est obligatoire.")
        @Size(max = 255, message = "L'adresse ne peut excéder 255 caractères.")
        String adresse,

        @NotNull(message = "Le mode de nomination du gérant est obligatoire.")
        ModeNominationGerant modeNomination,

        @Size(max = 50, message = "La durée du mandat ne peut excéder 50 caractères.")
        String dureeMandat,

        @Size(max = 500, message = "Les pouvoirs ne peuvent excéder 500 caractères.")
        String pouvoirs,

        Boolean actif
) {
    public GerantDto(UUID id, UUID associeId, boolean estAssocie, String nom, String prenom,
                     String cinOuPasseport, String nationalite, String adresse,
                     ModeNominationGerant modeNomination, String dureeMandat, String pouvoirs) {
        this(id, associeId, estAssocie, nom, prenom, cinOuPasseport, nationalite, adresse,
                modeNomination, dureeMandat, pouvoirs, true);
    }
}
