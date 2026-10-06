package com.backend.dto.admin;

import java.util.List;

public record PagedAgentResponse(
        List<AgentAdminResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
