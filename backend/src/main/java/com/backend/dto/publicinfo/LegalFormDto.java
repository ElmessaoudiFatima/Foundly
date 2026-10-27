package com.backend.dto.publicinfo;

import com.backend.enums.LegalFormCode;

import java.math.BigDecimal;
import java.util.List;

public record LegalFormDto(
        LegalFormCode code,
        String label,
        String shortDescription,
        String longDescription,
        BigDecimal minimumCapitalMad,
        Integer minPartners,
        Integer maxPartners,
        boolean requiresStatuts,
        boolean requiresNegativeCertificate,
        boolean requiresCapitalBlockageThreshold,
        BigDecimal indicativeTotalCostMad,
        Integer indicativeDurationDays,
        List<String> keyAdvantages,
        List<String> requiredDocumentsSummary
) {}
