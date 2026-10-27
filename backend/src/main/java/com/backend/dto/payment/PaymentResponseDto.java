package com.backend.dto.payment;

import com.backend.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponseDto(
        UUID id,
        UUID dossierId,
        UUID stepTrackingId,
        String stepLabel,
        BigDecimal amountMad,
        PaymentStatus status,
        String simulatedReference,
        String method,
        String cardNumberMasked,
        Instant createdAt,
        Instant confirmedAt
) {}
