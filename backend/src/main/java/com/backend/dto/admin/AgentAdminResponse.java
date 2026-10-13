package com.backend.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record AgentAdminResponse(
        UUID id,
        String firstName,
        String lastName,
        String fullName,
        String initials,
        String email,
        String organization,
        boolean active,
        String lastActivityFormatted,
        Instant lastActivityAt,
        Instant createdAt,
        String generatedPassword
) {
    public AgentAdminResponse(
            UUID id,
            String firstName,
            String lastName,
            String fullName,
            String initials,
            String email,
            String organization,
            boolean active,
            String lastActivityFormatted,
            Instant lastActivityAt,
            Instant createdAt
    ) {
        this(id, firstName, lastName, fullName, initials, email, organization, active, lastActivityFormatted, lastActivityAt, createdAt, null);
    }
}
