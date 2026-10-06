package com.backend.dto.admin;

import java.util.List;

public record AdminDashboardResponse(
        long totalDossiers,
        String totalDossiersChange,
        long dossiersEnCours,
        String dossiersEnCoursPct,
        long dossiersFinalises,
        String dossiersFinalisesChange,
        double tauxRejet,
        String tauxRejetChange,
        double dureeMoyenneJours,
        String dureeMoyenneChange,
        String monthlyTrendPct,
        List<MonthlyVolumeItem> monthlyVolume,
        List<FormeDistributionItem> repartitionFormes
) {
    public record MonthlyVolumeItem(
            String month,
            int count
    ) {}

    public record FormeDistributionItem(
            String code,
            String label,
            long count,
            int percentage,
            String color
    ) {}
}
