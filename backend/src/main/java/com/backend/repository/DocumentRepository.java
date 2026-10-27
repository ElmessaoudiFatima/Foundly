package com.backend.repository;

import com.backend.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    List<DocumentEntity> findByDossierId(UUID dossierId);

    List<DocumentEntity> findByDossierIdOrderByUploadedAtDesc(UUID dossierId);

    Optional<DocumentEntity> findByIdAndDossierId(UUID id, UUID dossierId);

    Optional<DocumentEntity> findFirstByDossierIdAndDocumentTypeCodeOrderByUploadedAtDesc(UUID dossierId, String documentTypeCode);

    boolean existsByDossierIdAndDocumentTypeCode(UUID dossierId, String documentTypeCode);

    void deleteByIdAndDossierId(UUID id, UUID dossierId);
}
