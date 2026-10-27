package com.backend.dto.document;

import com.backend.enums.DocumentSource;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponseDto(
        UUID id,
        String documentTypeCode,
        String documentTypeLabel,
        String fileName,
        String mimeType,
        Long sizeBytes,
        DocumentSource source,
        Instant uploadedAt,
        UUID uploadedByUserId,
        String note,
        String contentUrl
) {}
