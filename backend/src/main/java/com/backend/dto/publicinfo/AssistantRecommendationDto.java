package com.backend.dto.publicinfo;

import com.backend.enums.LegalFormCode;

import java.util.List;

public record AssistantRecommendationDto(
        LegalFormCode recommendedLegalForm,
        String recommendedLegalFormLabel,
        List<LegalFormCode> alternatives,
        String rationale,
        List<String> indicativeChecklist,
        String capitalAdvice,
        String taxRegimeSummary,
        String disclaimer
) {}
