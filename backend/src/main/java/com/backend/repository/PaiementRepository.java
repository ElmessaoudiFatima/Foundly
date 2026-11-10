package com.backend.repository;

import com.backend.entity.Paiement;
import com.backend.enums.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaiementRepository extends JpaRepository<Paiement, UUID> {

    List<Paiement> findByDossierIdOrderByCreatedAtDesc(UUID dossierId);

    @EntityGraph(attributePaths = {"dossier", "suiviEtape"})
    Optional<Paiement> findByIdAndDossierId(UUID id, UUID dossierId);

    Optional<Paiement> findByReferenceSimulation(String referenceSimulation);

    Optional<Paiement> findFirstByDossierIdAndSuiviEtapeIdAndStatut(UUID dossierId, UUID suiviEtapeId, PaymentStatus statut);

    boolean existsByDossierIdAndSuiviEtapeIdAndStatut(UUID dossierId, UUID suiviEtapeId, PaymentStatus statut);
}
