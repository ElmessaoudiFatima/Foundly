package com.backend.repository;

import com.backend.entity.Associe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssocieRepository extends JpaRepository<Associe, UUID> {
    List<Associe> findByDossierId(UUID dossierId);
    List<Associe> findByDossierIdAndActifTrue(UUID dossierId);
    void deleteByDossierId(UUID dossierId);
}
