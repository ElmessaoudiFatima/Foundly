package com.backend.repository;

import com.backend.entity.Dossier;
import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DossierRepository extends JpaRepository<Dossier, UUID>, JpaSpecificationExecutor<Dossier> {

    Optional<Dossier> findByReference(String reference);

    Optional<Dossier> findByIdAndEntrepreneurId(UUID id, UUID entrepreneurId);

    Page<Dossier> findByEntrepreneurId(UUID entrepreneurId, Pageable pageable);

    Page<Dossier> findByEntrepreneurIdAndStatut(UUID entrepreneurId, DossierStatus statut, Pageable pageable);

    long countByEntrepreneurId(UUID entrepreneurId);

    long countByEntrepreneurIdAndStatut(UUID entrepreneurId, DossierStatus statut);

    long countByStatut(DossierStatus statut);

    long countByFormeJuridique(LegalFormCode formeJuridique);

    @Query("SELECT d.formeJuridique, COUNT(d) FROM Dossier d GROUP BY d.formeJuridique")
    List<Object[]> countGroupedByFormeJuridique();

    @Query("SELECT d.statut, COUNT(d) FROM Dossier d GROUP BY d.statut")
    List<Object[]> countGroupedByStatut();
}
