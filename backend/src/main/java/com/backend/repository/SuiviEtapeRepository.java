package com.backend.repository;

import com.backend.entity.SuiviEtape;
import com.backend.enums.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SuiviEtapeRepository extends JpaRepository<SuiviEtape, UUID> {

    List<SuiviEtape> findByDossierIdOrderByOrdreAsc(UUID dossierId);

    Optional<SuiviEtape> findByDossierIdAndOrdre(UUID dossierId, Integer ordre);

    Optional<SuiviEtape> findByDossierIdAndEtapeCode(UUID dossierId, String etapeCode);

    Optional<SuiviEtape> findFirstByDossierIdAndStatut(UUID dossierId, StepStatus statut);

    List<SuiviEtape> findByDossierIdAndStatut(UUID dossierId, StepStatus statut);

    long countByDossierIdAndStatut(UUID dossierId, StepStatus statut);
}
