package com.backend.service;

import com.backend.dto.request.OrganizationCreateRequest;
import com.backend.dto.request.OrganizationUpdateRequest;
import com.backend.dto.response.OrganizationResponse;

import java.util.List;

/**
 * Contrat du service de gestion des organismes.
 */
public interface OrganismeService {

    /**
     * Retourne la liste de tous les organismes, triés par code ASC,
     * avec leur nombre d'agents actifs.
     */
    List<OrganizationResponse> listOrganizations();

    /**
     * Crée un nouvel organisme.
     *
     * @throws com.backend.exception.OrganizationCodeAlreadyUsedException si le code est déjà pris
     */
    OrganizationResponse createOrganization(OrganizationCreateRequest request);

    /**
     * Met à jour le nom et/ou la description d'un organisme existant.
     * Le code n'est jamais modifiable.
     *
     * @throws com.backend.exception.OrganizationNotFoundException      si le code est inconnu
     * @throws org.springframework.web.server.ResponseStatusException    si le corps est vide (422)
     */
    OrganizationResponse updateOrganization(String organizationCode, OrganizationUpdateRequest request);
}
