package com.backend.repository;

import com.backend.entity.Gerant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GerantRepository extends JpaRepository<Gerant, UUID> {
    List<Gerant> findByDossierId(UUID dossierId);
    List<Gerant> findByDossierIdAndActifTrue(UUID dossierId);
    void deleteByDossierId(UUID dossierId);
}
