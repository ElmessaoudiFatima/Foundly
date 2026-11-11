package com.backend.controller;

import com.backend.dto.document.DocumentResponseDto;
import com.backend.entity.Utilisateur;
import com.backend.repository.UtilisateurRepository;
import com.backend.service.DocumentService;
import com.backend.service.AgentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/dossiers/{dossierId}")
public class DocumentController {

    private final DocumentService documentService;
    private final UtilisateurRepository utilisateurRepository;
    private final AgentService agentService;

    public DocumentController(
            DocumentService documentService,
            UtilisateurRepository utilisateurRepository,
            AgentService agentService
    ) {
        this.documentService = documentService;
        this.utilisateurRepository = utilisateurRepository;
        this.agentService = agentService;
    }

    /**
     * POST /dossiers/{dossierId}/documents
     * Téléverser une pièce justificative (multipart/form-data)
     */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponseDto> uploadDocument(
            @PathVariable UUID dossierId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentTypeCode") String documentTypeCode,
            @RequestParam(value = "note", required = false) String note,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        assertAgentDossierAccess(dossierId, user);
        DocumentResponseDto res = documentService.uploadDocument(dossierId, file, documentTypeCode, note, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /**
     * GET /dossiers/{dossierId}/documents/{documentId}/content
     * Télécharger ou prévisualiser le document
     */
    @GetMapping("/documents/{documentId}/content")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable UUID dossierId,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        assertAgentDossierAccess(dossierId, user);
        Resource resource = documentService.getDocumentContent(dossierId, documentId, user);
        if (user.getRole() == com.backend.enums.RoleUtilisateur.AGENT) {
            agentService.auditDocumentConsultation(dossierId, documentId, user);
        }
        DocumentResponseDto metadata = documentService.getDocumentMetadata(dossierId, documentId, user);

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            mediaType = MediaType.parseMediaType(metadata.mimeType());
        } catch (Exception ignored) {}

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + metadata.fileName() + "\"")
                .body(resource);
    }

    /**
     * GET /dossiers/{dossierId}/documents/{documentId}
     * Métadonnées d'un document
     */
    @GetMapping("/documents/{documentId}")
    public ResponseEntity<DocumentResponseDto> getDocumentMetadata(
            @PathVariable UUID dossierId,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        assertAgentDossierAccess(dossierId, user);
        DocumentResponseDto res = documentService.getDocumentMetadata(dossierId, documentId, user);
        return ResponseEntity.ok(res);
    }

    /**
     * DELETE /dossiers/{dossierId}/documents/{documentId}
     * Supprimer un document
     */
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID dossierId,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        assertAgentDossierAccess(dossierId, user);
        documentService.deleteDocument(dossierId, documentId, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /dossiers/{dossierId}/generated-documents/{type}
     * Générer un document pré-rempli (STATUTS ou FORMULAIRE_UNIQUE)
     */
    @PostMapping("/generated-documents/{type}")
    public ResponseEntity<DocumentResponseDto> generateDocument(
            @PathVariable UUID dossierId,
            @PathVariable String type,
            Authentication authentication
    ) {
        Utilisateur user = getAuthenticatedUser(authentication);
        assertAgentDossierAccess(dossierId, user);
        DocumentResponseDto res = documentService.generateDocument(dossierId, type, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    private Utilisateur getAuthenticatedUser(Authentication authentication) {
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal.getUsername());
        return utilisateurRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur authentifié introuvable : " + userId));
    }

    private void assertAgentDossierAccess(UUID dossierId, Utilisateur user) {
        if (user.getRole() == com.backend.enums.RoleUtilisateur.AGENT) {
            agentService.assertClaimedByAgent(dossierId, user);
        }
    }
}
