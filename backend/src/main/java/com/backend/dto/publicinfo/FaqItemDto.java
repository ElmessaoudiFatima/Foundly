package com.backend.dto.publicinfo;

public record FaqItemDto(
        String id,
        String category,
        String question,
        String answer
) {}
