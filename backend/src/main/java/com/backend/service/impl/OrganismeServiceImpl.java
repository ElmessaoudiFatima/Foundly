package com.backend.service.impl;

import com.backend.dto.request.OrganizationCreateRequest;
import com.backend.dto.request.OrganizationUpdateRequest;
import com.backend.dto.response.OrganizationResponse;
import com.backend.entity.Organisme;
import com.backend.exception.OrganizationCodeAlreadyUsedException;
import com.backend.exception.OrganizationNotFoundException;
import com.backend.mapper.OrganismeMapper;
import com.backend.repository.OrganismeRepository;
import com.backend.service.OrganismeService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implémentation du service de gestion des organismes.
 * Respecte le schéma strict Controller -> Service -> Repository.
 * Injection par constructeur uniquement.
 */
@Service
public class OrganismeServiceImpl implements OrganismeService {

    private final OrganismeRepository organismeRepository;
    private final OrganismeMapper organismeMapper;

    public OrganismeServiceImpl(OrganismeRepository organismeRepository,
                                OrganismeMapper organismeMapper) {
        this.organismeRepository = organismeRepository;
        this.organismeMapper     = organismeMapper;
    }

    // -----------------------------------------------------------------------
    // Lecture
    // -----------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponse> listOrganizations() {
        return organismeRepository.findAllWithActiveAgentCount()
                .stream()
                .map(organismeMapper::projectionToResponse)
                .toList();
    }

    // -----------------------------------------------------------------------
    // Création
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    public OrganizationResponse createOrganization(OrganizationCreateRequest request) {
        // Normalisation du code : trim + majuscules (avant toute vérification)
        String normalizedCode = request.code().trim().toUpperCase();

        // Vérification unicité en amont (évite de dépendre uniquement de la contrainte DB)
        if (organismeRepository.existsByCode(normalizedCode)) {
            throw new OrganizationCodeAlreadyUsedException(normalizedCode);
        }

        Organisme organisme = organismeMapper.toEntity(request, normalizedCode);

        try {
            Organisme saved = organismeRepository.save(organisme);
            return organismeMapper.toResponse(saved, 0L);
        } catch (DataIntegrityViolationException ex) {
            // Race condition : le code a été créé entre la vérification et le save
            throw new OrganizationCodeAlreadyUsedException(normalizedCode);
        }
    }

    // -----------------------------------------------------------------------
    // Mise à jour partielle
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    public OrganizationResponse updateOrganization(String organizationCode,
                                                   OrganizationUpdateRequest request) {
        // Corps vide : aucun champ à mettre à jour
        if (request == null
                || (request.name() == null && request.description() == null)) {
            throw new IllegalArgumentException(
                    "Le corps de la requête est vide : au moins 'name' ou 'description' est requis.");
        }

        Organisme organisme = organismeRepository.findByCode(organizationCode)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationCode));

        if (request.name() != null && !request.name().isBlank()) {
            organisme.setNom(request.name().trim());
        }
        if (request.description() != null) {
            organisme.setDescription(request.description().trim());
        }

        Organisme saved = organismeRepository.save(organisme);

        // Recompute activeAgentCount via projection pour le retour
        long agentCount = organismeRepository.findAllWithActiveAgentCount()
                .stream()
                .filter(p -> p.getCode().equals(saved.getCode()))
                .mapToLong(p -> p.getActiveAgentCount() != null ? p.getActiveAgentCount() : 0L)
                .findFirst()
                .orElse(0L);

        return organismeMapper.toResponse(saved, agentCount);
    }
}
