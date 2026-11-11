package com.backend.dto.dossier;

import com.backend.enums.ActorType;
import com.backend.enums.StepStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StepTrackingDto(
        UUID id,
        String code,
        String label,
        Integer order,
        StepStatus status,
        ActorType actionOwner,
        String organizationCode,
        String organizationName,
        Integer indicativeDurationDays,
        BigDecimal feeMad,
        boolean paymentRequired,
        Instant startedAt,
        Instant decidedAt,
        String rejectionReason,
        String decidedBy,
        Map<String, Boolean> checklistReview,
        Map<String, String> validationResult
) {
    public StepTrackingDto(
            UUID id,
            String code,
            String label,
            Integer order,
            StepStatus status,
            ActorType actionOwner,
            String organizationCode,
            String organizationName,
            Integer indicativeDurationDays,
            BigDecimal feeMad,
            boolean paymentRequired,
            Instant startedAt,
            Instant decidedAt,
            String rejectionReason
    ) {
        this(id, code, label, order, status, actionOwner, organizationCode, organizationName,
                indicativeDurationDays, feeMad, paymentRequired, startedAt, decidedAt,
                rejectionReason, null, Map.of(), Map.of());
    }
}
