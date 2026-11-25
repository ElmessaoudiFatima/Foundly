package com.backend.dto.document;

import com.backend.enums.DocumentSource;
import com.backend.enums.DocumentReviewStatus;

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
        String contentUrl,
        DocumentReviewStatus reviewStatus,
        String reviewComment,
        String reviewedByAgentName,
        Instant reviewedAt,
        UUID associeId,
        UUID gerantId
) {
    public DocumentResponseDto(
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
            String contentUrl,
            DocumentReviewStatus reviewStatus,
            String reviewComment,
            String reviewedByAgentName,
            Instant reviewedAt
    ) {
        this(id, documentTypeCode, documentTypeLabel, fileName, mimeType, sizeBytes, source,
                uploadedAt, uploadedByUserId, note, contentUrl, reviewStatus,
                reviewComment, reviewedByAgentName, reviewedAt, null, null);
    }

    public DocumentResponseDto(
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
    ) {
        this(id, documentTypeCode, documentTypeLabel, fileName, mimeType, sizeBytes, source,
                uploadedAt, uploadedByUserId, note, contentUrl, DocumentReviewStatus.A_VERIFIER,
                null, null, null, null, null);
    }
}
