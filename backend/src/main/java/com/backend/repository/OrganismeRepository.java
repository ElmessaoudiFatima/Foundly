package com.backend.repository;

import com.backend.entity.Organisme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA pour l'entité Organisme.
 * La requête agrégée evite tout problème N+1 sur le comptage des agents actifs.
 */
@Repository
public interface OrganismeRepository extends JpaRepository<Organisme, UUID> {

    Optional<Organisme> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Retourne tous les organismes avec le nombre d'agents actifs (rôle AGENT, actif = true)
     * rattachés à chacun, via une jointure agrégée sur la table utilisateur.
     * Tri : code ASC.
     */
    @Query("""
        SELECT o.code   AS code,
               o.nom    AS nom,
               o.description AS description,
               COUNT(u.id)   AS activeAgentCount
        FROM Organisme o
        LEFT JOIN Utilisateur u
            ON u.organisme = o.code
            AND u.role     = com.backend.enums.RoleUtilisateur.AGENT
            AND u.actif    = true
        GROUP BY o.id, o.code, o.nom, o.description
        ORDER BY o.code ASC
        """)
    List<OrganismeAgentCountProjection> findAllWithActiveAgentCount();

    /**
     * Projection légère : code, nom, description et nombre d'agents actifs.
     */
    interface OrganismeAgentCountProjection {
        String getCode();
        String getNom();
        String getDescription();
        Long getActiveAgentCount();
    }
}
