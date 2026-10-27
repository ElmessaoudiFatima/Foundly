package com.backend.dto.dossier;

public record StepReferenceDto(
        String code,
        String label,
        Integer order
) {}
