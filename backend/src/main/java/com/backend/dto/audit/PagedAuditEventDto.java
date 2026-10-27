package com.backend.dto.audit;

import java.util.List;

public record PagedAuditEventDto(
        List<AuditEventDto> items,
        long totalElements,
        int totalPages,
        int page,
        int size
) {}
