package com.backend.dto.admin;

import java.util.List;

public record PagedDossierResponse(
        List<DossierAdminResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
