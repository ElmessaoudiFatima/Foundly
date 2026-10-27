package com.backend.service;

import com.backend.dto.document.DocumentResponseDto;
import com.backend.dto.dossier.ChecklistItemDto;
import com.backend.entity.Utilisateur;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface DocumentService {

    DocumentResponseDto uploadDocument(
            UUID dossierId,
            MultipartFile file,
            String documentTypeCode,
            String note,
            Utilisateur currentUser
    );

    Resource getDocumentContent(UUID dossierId, UUID documentId, Utilisateur currentUser);

    DocumentResponseDto getDocumentMetadata(UUID dossierId, UUID documentId, Utilisateur currentUser);

    void deleteDocument(UUID dossierId, UUID documentId, Utilisateur currentUser);

    DocumentResponseDto generateDocument(UUID dossierId, String type, Utilisateur currentUser);

    List<ChecklistItemDto> getChecklist(UUID dossierId, Utilisateur currentUser);
}
