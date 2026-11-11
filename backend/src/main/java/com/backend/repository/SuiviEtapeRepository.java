package com.backend.repository;

import com.backend.entity.SuiviEtape;
import com.backend.enums.StepStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SuiviEtapeRepository extends JpaRepository<SuiviEtape, UUID> {

    List<SuiviEtape> findByDossierIdOrderByOrdreAsc(UUID dossierId);

    Optional<SuiviEtape> findByDossierIdAndOrdre(UUID dossierId, Integer ordre);

    Optional<SuiviEtape> findByDossierIdAndEtapeCode(UUID dossierId, String etapeCode);

    Optional<SuiviEtape> findFirstByDossierIdAndStatut(UUID dossierId, StepStatus statut);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SuiviEtape> findFirstByDossierIdAndStatutOrderByOrdreAsc(UUID dossierId, StepStatus statut);

    List<SuiviEtape> findByDossierIdAndStatut(UUID dossierId, StepStatus statut);

    long countByDossierIdAndStatut(UUID dossierId, StepStatus statut);

    // ── Méthodes optimisées pour l'espace agent administratif (P2) ──

    /** Toutes les étapes ayant un statut donné (pour la file d'attente agent). */
    List<SuiviEtape> findByStatut(StepStatus statut);

    /** Étapes traitées (VALIDEE / REJETEE) par un agent donné — pour l'historique agent. */
    List<SuiviEtape> findByAgentIdAndStatutIn(UUID agentId, List<StepStatus> statuts);

    /**
     * Requête optimisée (JOIN FETCH) pour charger instantanément les étapes actives,
     * leurs dossiers associés et l'entrepreneur en UNE SEULE requête SQL.
     */
    @Query("SELECT s FROM SuiviEtape s " +
           "JOIN FETCH s.dossier d " +
           "LEFT JOIN FETCH d.entrepreneur e " +
           "WHERE s.statut = :statut " +
           "AND d.statut NOT IN (com.backend.enums.DossierStatus.FINALISE, com.backend.enums.DossierStatus.BROUILLON) " +
           "ORDER BY d.createdAt DESC")
    List<SuiviEtape> findActiveStepsWithDossier(@Param("statut") StepStatus statut);

    /**
     * Requête optimisée (JOIN FETCH) pour charger l'historique des décisions de l'agent,
     * les dossiers associés et l'entrepreneur en UNE SEULE requête SQL.
     */
    @Query("SELECT s FROM SuiviEtape s " +
           "JOIN FETCH s.dossier d " +
           "LEFT JOIN FETCH d.entrepreneur e " +
           "WHERE s.agent.id = :agentId AND s.statut IN :statuts " +
           "ORDER BY s.decidedAt DESC")
    List<SuiviEtape> findHistoryByAgentIdWithDossier(
            @Param("agentId") UUID agentId,
            @Param("statuts") List<StepStatus> statuts
    );
}
