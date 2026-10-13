package com.backend.dto.admin;

import jakarta.validation.constraints.NotNull;

public record AgentUpdateStatusRequest(
        @NotNull(message = "Le statut actif est obligatoire")
        Boolean active,

        String reason
) {}
