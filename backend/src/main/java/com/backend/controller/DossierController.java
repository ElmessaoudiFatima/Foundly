package com.backend.controller;

import com.backend.dto.audit.PagedAuditEventDto;
import com.backend.dto.dossier.*;
import com.backend.entity.Utilisateur;
import com.backend.enums.DossierStatus;
import com.backend.repository.UtilisateurRepository;
import com.backend.service.DocumentService;
import com.backend.service.DossierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/dossiers")
public class DossierController {

    private final DossierService dossierService;
    private final DocumentService documentService;
    private final UtilisateurRepository utilisateurRepository;

    public DossierController(
            DossierService dossierService,
            DocumentService documentService,
            UtilisateurRepository utilisateurRepository
    ) {
        this.dossierService = dossierService;
        this.documentService = documentService;
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * GET /dossiers
     * Lister les dossiers de l'entrepreneur connecté
     */
    @GetMapping
    public ResponseEntity<PagedDossierSummaryDto> getMyDossiers(
            @RequestParam(required = false) DossierStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        PagedDossierSummaryDto res = dossierService.getEntrepreneurDossiers(user.getId(), status, page, size);
        return ResponseEntity.ok(res);
    }

    /**
     * POST /dossiers
     * Créer un nouveau dossier d'entreprise
     */
    @PostMapping
    public ResponseEntity<DossierDetailDto> createDossier(
            @Valid @RequestBody DossierCreateRequest request,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        DossierDetailDto res = dossierService.createDossier(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /**
     * GET /dossiers/{dossierId}
     * Consulter un dossier et son avancement détaillé
     */
    @GetMapping("/{dossierId}")
    public ResponseEntity<DossierDetailDto> getDossier(
            @PathVariable UUID dossierId,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        DossierDetailDto res = dossierService.getDossierDetail(dossierId, user);
        return ResponseEntity.ok(res);
    }

    /**
     * PUT /dossiers/{dossierId}
     * Mettre à jour les informations d'un dossier
     */
    @PutMapping("/{dossierId}")
    public ResponseEntity<DossierDetailDto> updateDossier(
            @PathVariable UUID dossierId,
            @Valid @RequestBody DossierUpdateRequest request,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        DossierDetailDto res = dossierService.updateDossier(dossierId, request, user);
        return ResponseEntity.ok(res);
    }

    /**
     * GET /dossiers/{dossierId}/checklist
     * Lire la checklist documentaire du dossier
     */
    @GetMapping("/{dossierId}/checklist")
    public ResponseEntity<List<ChecklistItemDto>> getChecklist(
            @PathVariable UUID dossierId,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        List<ChecklistItemDto> res = documentService.getChecklist(dossierId, user);
        return ResponseEntity.ok(res);
    }

    /**
     * GET /dossiers/{dossierId}/steps
     * Lire les suivis d'étape du workflow
     */
    @GetMapping("/{dossierId}/steps")
    public ResponseEntity<List<StepTrackingDto>> getSteps(
            @PathVariable UUID dossierId,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        DossierDetailDto detail = dossierService.getDossierDetail(dossierId, user);
        return ResponseEntity.ok(detail.steps());
    }

    /**
     * GET /dossiers/{dossierId}/audit-events
     * Consulter l'historique d'audit non modifiable
     */
    @GetMapping("/{dossierId}/audit-events")
    public ResponseEntity<PagedAuditEventDto> getAuditEvents(
            @PathVariable UUID dossierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        PagedAuditEventDto res = dossierService.getAuditEvents(dossierId, page, size, user);
        return ResponseEntity.ok(res);
    }

    /**
     * POST /dossiers/{dossierId}/submit
     * Soumettre le dossier au guichet unique
     */
    @PostMapping("/{dossierId}/submit")
    public ResponseEntity<SubmissionResultDto> submitDossier(
            @PathVariable UUID dossierId,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        SubmissionResultDto res = dossierService.submitDossier(dossierId, user);
        return ResponseEntity.ok(res);
    }

    private Utilisateur getAuthenticatedUser(Authentication authentication) {
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal.getUsername());
        return utilisateurRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur authentifié introuvable : " + userId));
    }
}
