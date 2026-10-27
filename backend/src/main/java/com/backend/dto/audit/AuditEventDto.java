package com.backend.dto.audit;

import com.backend.enums.ActorType;
import com.backend.enums.AuditEventAction;

import java.time.Instant;
import java.util.UUID;

public record AuditEventDto(
        UUID id,
        Instant occurredAt,
        AuditEventAction action,
        ActorType actorType,
        String actorNom,
        UUID stepTrackingId,
        String reason,
        String details
) {}
