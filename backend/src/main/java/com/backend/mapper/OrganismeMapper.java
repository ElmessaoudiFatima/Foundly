package com.backend.mapper;

import com.backend.dto.request.OrganizationCreateRequest;
import com.backend.dto.response.OrganizationResponse;
import com.backend.entity.Organisme;
import com.backend.repository.OrganismeRepository.OrganismeAgentCountProjection;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Conversions manuelles entre Organisme (entité / projection) et OrganizationResponse.
 * Pas de MapStruct, pas de Lombok, pas de logique métier.
 */
@Component
public class OrganismeMapper {

    /**
     * OrganizationCreateRequest + code normalisé → Organisme (entité non persistée).
     */
    public Organisme toEntity(OrganizationCreateRequest request, String normalizedCode) {
        Organisme o = new Organisme();
        o.setCode(normalizedCode);
        o.setNom(request.name().trim());
        o.setDescription(request.description() != null ? request.description().trim() : null);
        return o;
    }

    /**
     * Organisme (entité) + activeAgentCount calculé → OrganizationResponse.
     * managedSteps est vide : les entités Workflow/Étape ne sont pas encore implémentées.
     */
    public OrganizationResponse toResponse(Organisme organisme, long activeAgentCount) {
        return new OrganizationResponse(
                organisme.getCode(),
                organisme.getNom(),
                organisme.getDescription(),
                activeAgentCount,
                List.of()
        );
    }

    /**
     * Projection agrégée (LEFT JOIN GROUP BY) → OrganizationResponse.
     * managedSteps est vide pour la même raison.
     */
    public OrganizationResponse projectionToResponse(OrganismeAgentCountProjection projection) {
        long count = projection.getActiveAgentCount() != null
                ? projection.getActiveAgentCount()
                : 0L;
        return new OrganizationResponse(
                projection.getCode(),
                projection.getNom(),
                projection.getDescription(),
                count,
                List.of()
        );
    }
}
