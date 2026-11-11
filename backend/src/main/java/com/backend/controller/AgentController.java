package com.backend.controller;

import com.backend.dto.agent.AgentHistoryItemDto;
import com.backend.dto.agent.AgentDocumentReviewRequest;
import com.backend.dto.agent.AgentQueueItemDto;
import com.backend.dto.agent.AgentRejectRequest;
import com.backend.dto.agent.AgentValidateRequest;
import com.backend.dto.audit.PagedAuditEventDto;
import com.backend.dto.document.DocumentResponseDto;
import com.backend.dto.dossier.DossierDetailDto;
import com.backend.enums.DocumentType;
import com.backend.entity.Utilisateur;
import com.backend.repository.DocumentRepository;
import com.backend.repository.UtilisateurRepository;
import com.backend.service.AgentService;
import com.backend.service.DocumentService;
import com.backend.service.DossierService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Arrays;
import java.util.UUID;

/**
 * Contrôleur REST dédié à l'espace agent administratif.
 *
 * Toutes les routes sont sous /agent/** et sont sécurisées par Spring Security
 * (SecurityConfig → hasRole("AGENT")).
 *
 * Réf. CDC § 8.2 | RG5, RG7, RG8, RG15
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;
    private final DossierService dossierService;
    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final UtilisateurRepository utilisateurRepository;

    public AgentController(
            AgentService agentService,
            DossierService dossierService,
            DocumentService documentService,
            DocumentRepository documentRepository,
            UtilisateurRepository utilisateurRepository
    ) {
        this.agentService = agentService;
        this.dossierService = dossierService;
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * GET /agent/queue
     * Retourne la file d'attente des dossiers à traiter pour l'organisme de l'agent connecté.
     * Réf. CDC § 8.2.
     */
    @GetMapping("/queue")
    public ResponseEntity<List<AgentQueueItemDto>> getQueue(Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        return ResponseEntity.ok(agentService.getQueue(agent));
    }

    /**
     * Prend en charge l'étape courante de façon exclusive pour l'agent.
     */
    @PostMapping({"/dossiers/{id}/claim", "/dossiers/{id}/prise-en-charge"})
    public ResponseEntity<AgentQueueItemDto> takeInCharge(
            @PathVariable UUID id,
            Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        return ResponseEntity.ok(agentService.takeInCharge(id, agent));
    }

    /**
     * Libère explicitement le verrou détenu par l'agent courant.
     */
    @DeleteMapping("/dossiers/{id}/claim")
    public ResponseEntity<Void> releaseClaim(
            @PathVariable UUID id,
            Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        agentService.releaseClaim(id, agent);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /agent/dossiers/{id}
     * Consulte le détail complet d'un dossier (étapes, infos, progression).
     * L'agent peut lire tout dossier EN_COURS de son organisme.
     * Réf. CDC § 8.2.
     */
    @GetMapping("/dossiers/{id}")
    public ResponseEntity<DossierDetailDto> getDossierDetail(
            @PathVariable UUID id,
            Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        agentService.assertDossierAccess(id, agent);
        DossierDetailDto detail = dossierService.getDossierDetailForAgent(id, agent.getOrganisme());
        return ResponseEntity.ok(detail);
    }

    /**
     * GET /agent/dossiers/{id}/documents
     * Consulte la liste des documents déposés par l'entrepreneur.
     * Réf. CDC § 8.2 — Consultation des documents déposés.
     */
    @GetMapping("/dossiers/{id}/documents")
    public ResponseEntity<List<DocumentResponseDto>> getDocuments(
            @PathVariable UUID id,
            Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        agentService.assertDossierAccess(id, agent);
        List<DocumentResponseDto> docs = documentRepository
                .findByDossierIdOrderByUploadedAtDesc(id)
                .stream()
                .filter(d -> agentService.isDocumentTypeAllowedForAgent(d.getDocumentTypeCode(), agent))
                .map(this::toDocumentResponse)
                .toList();
        return ResponseEntity.ok(docs);
    }

    @GetMapping("/history/{id}")
    public ResponseEntity<DossierDetailDto> getHistoricalDossier(
            @PathVariable UUID id,
            Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        agentService.assertHistoricalDossierAccess(id, agent);
        return ResponseEntity.ok(dossierService.getDossierDetailForAgent(id, agent.getOrganisme()));
    }

    @GetMapping("/history/{id}/documents")
    public ResponseEntity<List<DocumentResponseDto>> getHistoricalDocuments(
            @PathVariable UUID id,
            Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        agentService.assertHistoricalDossierAccess(id, agent);
        List<DocumentResponseDto> documents = documentRepository.findByDossierIdOrderByUploadedAtDesc(id).stream()
                .filter(document -> agentService.isDocumentTypeAllowedForAgent(document.getDocumentTypeCode(), agent))
                .map(this::toDocumentResponse)
                .toList();
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/history/{id}/audit-events")
    public ResponseEntity<PagedAuditEventDto> getHistoricalAuditEvents(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        return ResponseEntity.ok(agentService.getHistoricalAuditEvents(id, page, size, agent));
    }

    @GetMapping("/history/{id}/documents/{documentId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> previewHistoricalDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        agentService.assertHistoricalDocumentAccess(id, documentId, agent);
        org.springframework.core.io.Resource content = documentService.getDocumentContent(id, documentId, agent);
        agentService.auditHistoricalDocumentConsultation(id, documentId, agent);
        DocumentResponseDto metadata = documentService.getDocumentMetadata(id, documentId, agent);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(metadata.mimeType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + metadata.fileName() + "\"")
                .body(content);
    }

    @PostMapping(value = "/dossiers/{id}/documents", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponseDto> uploadAgentDocument(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentTypeCode") String documentTypeCode,
            @RequestParam(value = "note", required = false) String note,
            Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        if (!agentService.isDocumentTypeAllowedForAgent(documentTypeCode, agent)) {
            throw new com.backend.exception.UnauthorizedDossierAccessException(
                    "Ce type de document n'est pas accepté par votre organisme.");
        }
        agentService.assertClaimedByAgent(id, agent);
        DocumentResponseDto document = documentService.uploadDocument(id, file, documentTypeCode, note, agent);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(document);
    }

    @PutMapping("/dossiers/{id}/documents/{documentId}/review")
    public ResponseEntity<Void> reviewDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @Valid @RequestBody AgentDocumentReviewRequest request,
            Authentication authentication) {
        agentService.reviewDocument(id, documentId, request, resolveAgent(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dossiers/{id}/documents/{documentId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> previewDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        agentService.assertDocumentAccess(id, documentId, agent);
        org.springframework.core.io.Resource content = documentService.getDocumentContent(id, documentId, agent);
        agentService.auditDocumentConsultation(id, documentId, agent);
        DocumentResponseDto metadata = documentService.getDocumentMetadata(id, documentId, agent);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(metadata.mimeType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + metadata.fileName() + "\"")
                .body(content);
    }

    /**
     * POST /agent/dossiers/{id}/validate
     * Valide l'étape courante du dossier — passage automatique à l'étape suivante (RG7).
     * La checklist et le résultat métier sont obligatoires et contrôlés selon l'organisme.
     * Réf. CDC § 8.2, RG7.
     */
    @PostMapping({"/dossiers/{id}/validate", "/dossiers/{id}/valider"})
    public ResponseEntity<Void> validateStep(
            @PathVariable UUID id,
            @RequestBody(required = false) AgentValidateRequest request,
            Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        agentService.validateStep(id, request, agent);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /agent/dossiers/{id}/reject
     * Rejette l'étape courante avec un motif obligatoire.
     * Passe le dossier au statut À corriger et notifie l'entrepreneur (RG8).
     * Body : { "motifRejet": "..." }
     * Réf. CDC § 8.2, RG8.
     */
    @PostMapping({"/dossiers/{id}/reject", "/dossiers/{id}/rejeter"})
    public ResponseEntity<Void> rejectStep(
            @PathVariable UUID id,
            @Valid @RequestBody AgentRejectRequest request,
            Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        agentService.rejectStep(id, request, agent);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /agent/history
     * Historique des étapes validées ou rejetées par l'agent connecté.
     * Réf. CDC § 8.2.
     */
    @GetMapping("/history")
    public ResponseEntity<List<AgentHistoryItemDto>> getHistory(Authentication authentication) {
        Utilisateur agent = resolveAgent(authentication);
        return ResponseEntity.ok(agentService.getHistory(agent));
    }

    /**
     * Résout l'entité Utilisateur depuis l'Authentication de Spring Security.
     * Comme configuré dans JwtAuthenticationFilter, le principal est un UserDetails
     * dont le username est l'UUID en String de l'utilisateur.
     */
    private Utilisateur resolveAgent(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("Session agent non authentifiée.");
        }
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal.getUsername());
        return utilisateurRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Agent introuvable en base : " + userId));
    }

    private DocumentResponseDto toDocumentResponse(com.backend.entity.DocumentEntity document) {
        String typeLabel = Arrays.stream(DocumentType.values())
                .filter(type -> type.name().equals(document.getDocumentTypeCode()))
                .map(DocumentType::getLabel)
                .findFirst()
                .orElse(document.getDocumentTypeCode());
        String contentUrl = "/dossiers/" + document.getDossier().getId()
                + "/documents/" + document.getId() + "/content";
        return new DocumentResponseDto(
                document.getId(),
                document.getDocumentTypeCode(),
                typeLabel,
                document.getOriginalFileName(),
                document.getMimeType(),
                document.getFileSize(),
                document.getSource(),
                document.getUploadedAt(),
                document.getUploadedBy().getId(),
                document.getNote(),
                contentUrl,
                document.getReviewStatus(),
                document.getReviewComment(),
                document.getReviewedByAgent() == null ? null
                        : document.getReviewedByAgent().getPrenom() + " " + document.getReviewedByAgent().getNom(),
                document.getReviewedAt()
        );
    }
}
