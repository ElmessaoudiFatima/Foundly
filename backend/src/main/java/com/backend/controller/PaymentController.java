package com.backend.controller;

import com.backend.dto.payment.PaymentConfirmRequest;
import com.backend.dto.payment.PaymentInitiateRequest;
import com.backend.dto.payment.PaymentResponseDto;
import com.backend.entity.Paiement;
import com.backend.entity.Utilisateur;
import com.backend.exception.PaymentNotFoundException;
import com.backend.repository.PaiementRepository;
import com.backend.repository.UtilisateurRepository;
import com.backend.service.PaymentService;
import com.backend.service.PdfGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/dossiers/{dossierId}/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PdfGenerationService pdfGenerationService;
    private final PaiementRepository paiementRepository;
    private final UtilisateurRepository utilisateurRepository;

    public PaymentController(
            PaymentService paymentService,
            PdfGenerationService pdfGenerationService,
            PaiementRepository paiementRepository,
            UtilisateurRepository utilisateurRepository
    ) {
        this.paymentService = paymentService;
        this.pdfGenerationService = pdfGenerationService;
        this.paiementRepository = paiementRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * POST /dossiers/{dossierId}/payments
     * Initier un règlement de frais simulé
     */
    @PostMapping
    public ResponseEntity<PaymentResponseDto> initiatePayment(
            @PathVariable UUID dossierId,
            @Valid @RequestBody PaymentInitiateRequest request,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        PaymentResponseDto res = paymentService.initiatePayment(dossierId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /**
     * POST /dossiers/{dossierId}/payments/{paymentId}/confirm
     * Confirmer le règlement simulé
     */
    @PostMapping("/{paymentId}/confirm")
    public ResponseEntity<PaymentResponseDto> confirmPayment(
            @PathVariable UUID dossierId,
            @PathVariable UUID paymentId,
            @Valid @RequestBody PaymentConfirmRequest request,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        PaymentResponseDto res = paymentService.confirmPayment(dossierId, paymentId, request, user);
        return ResponseEntity.ok(res);
    }

    /**
     * GET /dossiers/{dossierId}/payments
     * Lister les paiements simulés d'un dossier
     */
    @GetMapping
    public ResponseEntity<List<PaymentResponseDto>> getPayments(
            @PathVariable UUID dossierId,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        List<PaymentResponseDto> res = paymentService.getPayments(dossierId, user);
        return ResponseEntity.ok(res);
    }

    /**
     * GET /dossiers/{dossierId}/payments/{paymentId}/receipt
     * Télécharger le reçu officiel de paiement simulé en PDF
     */
    @GetMapping(value = "/{paymentId}/receipt", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReceipt(
            @PathVariable UUID dossierId,
            @PathVariable UUID paymentId,
            Authentication authentication
    ) {
        getAuthenticatedUser(authentication);

        Paiement p = paiementRepository.findByIdAndDossierId(paymentId, dossierId)
                .orElseThrow(() -> new PaymentNotFoundException("Paiement introuvable : " + paymentId));

        byte[] pdf = pdfGenerationService.generatePaymentReceiptPdf(p);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Recu_Paiement_" + p.getReferenceSimulation() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private Utilisateur getAuthenticatedUser(Authentication authentication) {
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal.getUsername());
        return utilisateurRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur authentifié introuvable : " + userId));
    }
}
