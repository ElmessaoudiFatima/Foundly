package com.backend.dto.publicinfo;

import com.backend.enums.LegalFormCode;

import java.math.BigDecimal;
import java.util.List;

public record SimulationDto(
        LegalFormCode legalFormCode,
        String legalFormLabel,
        BigDecimal indicativeTotalCostMad,
        Integer indicativeDurationDays,
        boolean capitalBlockageRequired,
        BigDecimal administrativeFeesBreakdown,
        BigDecimal legalPublicationFeesBreakdown,
        List<String> applicableSteps,
        String disclaimer
) {}
