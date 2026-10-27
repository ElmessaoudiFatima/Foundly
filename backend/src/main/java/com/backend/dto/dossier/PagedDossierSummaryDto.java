package com.backend.dto.dossier;

import java.util.List;

public record PagedDossierSummaryDto(
        List<DossierSummaryDto> items,
        long totalElements,
        int totalPages,
        int page,
        int size
) {}
