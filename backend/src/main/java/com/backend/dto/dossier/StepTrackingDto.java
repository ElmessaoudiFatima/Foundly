package com.backend.dto.dossier;

import com.backend.enums.ActorType;
import com.backend.enums.StepStatus;

import java.math.BigDecimal;
import java.time.Instant;
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
        String rejectionReason
) {}
