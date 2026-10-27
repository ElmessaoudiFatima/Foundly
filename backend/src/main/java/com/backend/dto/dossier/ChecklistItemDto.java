package com.backend.dto.dossier;

import java.util.List;
import java.util.UUID;

public record ChecklistItemDto(
        String documentTypeCode,
        String label,
        boolean required,
        List<String> acceptedMimeTypes,
        boolean submitted,
        UUID activeDocumentId,
        String activeDocumentName,
        boolean correctionRequired,
        String correctionReason
) {}
