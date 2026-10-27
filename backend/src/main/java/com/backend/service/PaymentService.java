package com.backend.service;

import com.backend.dto.payment.PaymentConfirmRequest;
import com.backend.dto.payment.PaymentInitiateRequest;
import com.backend.dto.payment.PaymentResponseDto;
import com.backend.entity.Utilisateur;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    PaymentResponseDto initiatePayment(UUID dossierId, PaymentInitiateRequest request, Utilisateur currentUser);

    PaymentResponseDto confirmPayment(UUID dossierId, UUID paymentId, PaymentConfirmRequest request, Utilisateur currentUser);

    List<PaymentResponseDto> getPayments(UUID dossierId, Utilisateur currentUser);
}
