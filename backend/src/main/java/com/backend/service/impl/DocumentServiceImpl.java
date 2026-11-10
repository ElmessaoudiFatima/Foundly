package com.backend.service.impl;

import com.backend.dto.document.DocumentResponseDto;
import com.backend.dto.dossier.ChecklistItemDto;
import com.backend.entity.AuditEvent;
import com.backend.entity.DocumentEntity;
import com.backend.entity.Dossier;
import com.backend.entity.Utilisateur;
import com.backend.enums.*;
import com.backend.exception.*;
import com.backend.repository.AuditEventRepository;
import com.backend.repository.DocumentRepository;
import com.backend.repository.DossierRepository;
import com.backend.service.DocumentService;
import com.backend.service.PdfGenerationService;
import com.backend.service.WorkflowEngineService;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 Mo maximum (RG6)
    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "jpg", "jpeg", "png");
    private static final List<String> ALLOWED_MIME_TYPES = List.of("application/pdf", "image/jpeg", "image/jpg", "image/png");

    private final DocumentRepository documentRepository;
    private final DossierRepository dossierRepository;
    private final AuditEventRepository auditEventRepository;
    private final PdfGenerationService pdfGenerationService;
    private final WorkflowEngineService workflowEngineService;

    @Value("${foundly.upload.dir:./uploads}")
    private String uploadBaseDir;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DossierRepository dossierRepository,
            AuditEventRepository auditEventRepository,
            PdfGenerationService pdfGenerationService,
            WorkflowEngineService workflowEngineService
    ) {
        this.documentRepository = documentRepository;
        this.dossierRepository = dossierRepository;
        this.auditEventRepository = auditEventRepository;
        this.pdfGenerationService = pdfGenerationService;
        this.workflowEngineService = workflowEngineService;
    }

    @Override
    @Transactional
    public DocumentResponseDto uploadDocument(
            UUID dossierId,
            MultipartFile file,
            String documentTypeCode,
            String note,
            Utilisateur currentUser
    ) {
        Dossier dossier = getDossierAndCheckAccess(dossierId, currentUser);

        if (dossier.getStatut() == DossierStatus.FINALISE) {
            throw new InvalidDossierStateException("Impossible de modifier ou d'ajouter des documents sur un dossier finalisé.");
        }

        validateUploadedFile(file);

        try {
            Path targetDir = Paths.get(uploadBaseDir, "dossiers", dossierId.toString()).toAbsolutePath().normalize();
            Files.createDirectories(targetDir);

            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
            String extension = FilenameUtils.getExtension(originalName).toLowerCase();
            String storedName = documentTypeCode + "_" + UUID.randomUUID() + "." + extension;
            Path targetFilePath = targetDir.resolve(storedName);

            Files.copy(file.getInputStream(), targetFilePath);

            DocumentEntity entity = new DocumentEntity();
            entity.setDossier(dossier);
            entity.setDocumentTypeCode(documentTypeCode);
            entity.setOriginalFileName(originalName);
            entity.setStoredFileName(storedName);
            entity.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
            entity.setFileSize(file.getSize());
            entity.setSource(DocumentSource.UPLOADED);
            entity.setFilePath(targetFilePath.toString());
            entity.setNote(note);
            entity.setUploadedBy(currentUser);
            entity.setUploadedAt(Instant.now());

            DocumentEntity saved = documentRepository.save(entity);

            // Audit
            auditEventRepository.save(new AuditEvent(
                    dossier,
                    AuditEventAction.DOCUMENT_AJOUTE,
                    ActorType.ENTREPRENEUR,
                    currentUser,
                    currentUser.getPrenom() + " " + currentUser.getNom(),
                    null,
                    null,
                    "Ajout de la pièce : " + originalName + " (Type : " + documentTypeCode + ")"
            ));

            // Si c'est l'attestation de blocage du capital social, validation automatique de l'étape 3 (RG15)
            if ("ATTESTATION_BLOCAGE_CAPITAL".equalsIgnoreCase(documentTypeCode)) {
                workflowEngineService.checkAndAutoValidateCapitalBlockageIfUploaded(dossier);
            }

            return mapToDto(saved);

        } catch (IOException e) {
            log.error("Erreur lors de l'enregistrement du fichier : {}", e.getMessage(), e);
            throw new RuntimeException("Erreur de stockage du document téléversé.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Resource getDocumentContent(UUID dossierId, UUID documentId, Utilisateur currentUser) {
        getDossierAndCheckAccess(dossierId, currentUser);

        DocumentEntity doc = documentRepository.findByIdAndDossierId(documentId, dossierId)
                .orElseThrow(() -> new NoSuchElementException("Document introuvable : " + documentId));

        File file = new File(doc.getFilePath());
        if (!file.exists()) {
            throw new NoSuchElementException("Le fichier physique est introuvable sur le serveur.");
        }

        return new FileSystemResource(file);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponseDto getDocumentMetadata(UUID dossierId, UUID documentId, Utilisateur currentUser) {
        getDossierAndCheckAccess(dossierId, currentUser);

        DocumentEntity doc = documentRepository.findByIdAndDossierId(documentId, dossierId)
                .orElseThrow(() -> new NoSuchElementException("Document introuvable : " + documentId));

        return mapToDto(doc);
    }

    @Override
    @Transactional
    public void deleteDocument(UUID dossierId, UUID documentId, Utilisateur currentUser) {
        Dossier dossier = getDossierAndCheckAccess(dossierId, currentUser);

        if (dossier.getStatut() != DossierStatus.BROUILLON && dossier.getStatut() != DossierStatus.A_CORRIGER) {
            throw new InvalidDossierStateException("La suppression de documents n'est autorisée qu'aux statuts Brouillon et À corriger.");
        }

        DocumentEntity doc = documentRepository.findByIdAndDossierId(documentId, dossierId)
                .orElseThrow(() -> new NoSuchElementException("Document introuvable : " + documentId));

        try {
            Files.deleteIfExists(Paths.get(doc.getFilePath()));
        } catch (IOException e) {
            log.warn("Impossible de supprimer le fichier physique {} : {}", doc.getFilePath(), e.getMessage());
        }

        documentRepository.delete(doc);

        auditEventRepository.save(new AuditEvent(
                dossier,
                AuditEventAction.DOCUMENT_SUPPRIME,
                ActorType.ENTREPRENEUR,
                currentUser,
                currentUser.getPrenom() + " " + currentUser.getNom(),
                null,
                null,
                "Suppression du document : " + doc.getOriginalFileName()
        ));
    }

    @Override
    @Transactional
    public DocumentResponseDto generateDocument(UUID dossierId, String type, Utilisateur currentUser) {
        Dossier dossier = getDossierAndCheckAccess(dossierId, currentUser);

        if (dossier.getStatut() != DossierStatus.BROUILLON && dossier.getStatut() != DossierStatus.A_CORRIGER) {
            throw new InvalidDossierStateException("La génération de modèles est autorisée uniquement pour un dossier Brouillon ou À corriger.");
        }
        if (dossier.getFormeJuridique() == LegalFormCode.AUTO_ENTREPRENEUR) {
            throw new IllegalArgumentException("Les modèles Statuts/CRI ne sont pas applicables au parcours Auto-Entrepreneur.");
        }

        byte[] pdfBytes;
        String docTypeCode;
        String fileName;

        if ("STATUTS".equalsIgnoreCase(type)) {
            pdfBytes = pdfGenerationService.generateStatutsPdf(dossier);
            docTypeCode = "STATUTS_SIGNES";
            fileName = "Statuts_Constitutifs_" + dossier.getReference() + ".pdf";
        } else if ("FORMULAIRE_UNIQUE".equalsIgnoreCase(type) || "CRI".equalsIgnoreCase(type)) {
            pdfBytes = pdfGenerationService.generateFormulaireUniqueCriPdf(dossier);
            docTypeCode = "FORMULAIRE_UNIQUE";
            fileName = "Formulaire_Unique_CRI_" + dossier.getReference() + ".pdf";
        } else {
            throw new IllegalArgumentException("Type de document à générer non supporté : " + type);
        }

        try {
            Path targetDir = Paths.get(uploadBaseDir, "dossiers", dossierId.toString(), "generated").toAbsolutePath().normalize();
            Files.createDirectories(targetDir);

            String storedName = docTypeCode + "_" + UUID.randomUUID() + ".pdf";
            Path targetFilePath = targetDir.resolve(storedName);
            Files.write(targetFilePath, pdfBytes);

            DocumentEntity entity = new DocumentEntity();
            entity.setDossier(dossier);
            entity.setDocumentTypeCode(docTypeCode);
            entity.setOriginalFileName(fileName);
            entity.setStoredFileName(storedName);
            entity.setMimeType("application/pdf");
            entity.setFileSize((long) pdfBytes.length);
            entity.setSource(DocumentSource.GENERATED);
            entity.setFilePath(targetFilePath.toString());
            entity.setNote("Document type généré automatiquement par la plateforme Foundly.");
            entity.setUploadedBy(currentUser);
            entity.setUploadedAt(Instant.now());

            DocumentEntity saved = documentRepository.save(entity);

            auditEventRepository.save(new AuditEvent(
                    dossier,
                    AuditEventAction.DOCUMENT_AJOUTE,
                    ActorType.SYSTEME,
                    null,
                    "Générateur Foundly",
                    null,
                    null,
                    "Génération automatique du document : " + fileName
            ));

            return mapToDto(saved);

        } catch (IOException e) {
            log.error("Erreur lors de l'enregistrement du PDF généré : {}", e.getMessage(), e);
            throw new RuntimeException("Échec de l'enregistrement du document généré.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChecklistItemDto> getChecklist(UUID dossierId, Utilisateur currentUser) {
        Dossier dossier = getDossierAndCheckAccess(dossierId, currentUser);
        List<DocumentEntity> uploadedDocs = documentRepository.findByDossierId(dossierId);

        List<ChecklistItemDto> items = new ArrayList<>();

        // 1. Pièce d'identité CIN (toujours obligatoire)
        items.add(buildChecklistItem("CIN", "Carte Nationale d'Identité (CIN / Passeport / Séjour)", true, uploadedDocs, dossier));

        // 2. Justificatif de siège social (toujours obligatoire)
        items.add(buildChecklistItem("JUSTIFICATIF_SIEGE", "Justificatif du siège social (Contrat de bail ou attestation de domiciliation)", true, uploadedDocs, dossier));

        // Formes Sociétés (SARL, SARL_AU, SA)
        if (dossier.getFormeJuridique() != LegalFormCode.AUTO_ENTREPRENEUR) {
            items.add(buildChecklistItem("STATUTS_SIGNES", "Statuts constitutifs signés de la société", true, uploadedDocs, dossier));
            items.add(buildChecklistItem("FORMULAIRE_UNIQUE", "Formulaire unique d'immatriculation CRI", true, uploadedDocs, dossier));

            // RG15 : Blocage de capital si SA ou SARL > 100 000 MAD
            if (dossier.isCapitalBlockageRequired()) {
                items.add(buildChecklistItem("ATTESTATION_BLOCAGE_CAPITAL", "Attestation bancaire de blocage du capital social (capital > 100 000 MAD)", true, uploadedDocs, dossier));
            } else {
                items.add(buildChecklistItem("ATTESTATION_BLOCAGE_CAPITAL", "Attestation bancaire de blocage (Non requise — dispense légale <= 100 000 MAD)", false, uploadedDocs, dossier));
            }
        } else {
            // Auto-Entrepreneur
            items.add(buildChecklistItem("FORMULAIRE_UNIQUE", "Formulaire d'inscription au Registre National de l'Auto-Entrepreneur (RNAE)", true, uploadedDocs, dossier));
        }

        return items;
    }

    private ChecklistItemDto buildChecklistItem(
            String code,
            String label,
            boolean required,
            List<DocumentEntity> uploadedDocs,
            Dossier dossier
    ) {
        // Un modèle Foundly est une aide au remplissage : seul un document ensuite téléversé
        // par l'entrepreneur peut satisfaire une pièce de la checklist (notamment les statuts signés).
        DocumentEntity activeDoc = uploadedDocs.stream()
                .filter(d -> d.getSource() == DocumentSource.UPLOADED)
                .filter(d -> d.getDocumentTypeCode().equalsIgnoreCase(code))
                .max(Comparator.comparing(DocumentEntity::getUploadedAt))
                .orElse(null);

        boolean submitted = activeDoc != null;
        boolean correctionRequired = false;
        String correctionReason = null;

        if (dossier.getStatut() == DossierStatus.A_CORRIGER && dossier.getRejectionReason() != null) {
            // Si le motif de rejet mentionne le document ou type
            if (dossier.getRejectionReason().toLowerCase().contains(code.toLowerCase().replace("_", " "))
                    || dossier.getRejectionReason().toLowerCase().contains(label.toLowerCase())) {
                correctionRequired = true;
                correctionReason = dossier.getRejectionReason();
            }
        }

        return new ChecklistItemDto(
                code,
                label,
                required,
                List.of("application/pdf", "image/jpeg", "image/png"),
                submitted,
                activeDoc != null ? activeDoc.getId() : null,
                activeDoc != null ? activeDoc.getOriginalFileName() : null,
                correctionRequired,
                correctionReason
        );
    }

    private void validateUploadedFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Le fichier envoyé est vide.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException("La taille du fichier (" + (file.getSize() / 1024 / 1024) + " Mo) dépasse la limite autorisée de 5 Mo (RG6).");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            throw new InvalidFileException("Nom de fichier manquant.");
        }

        String extension = FilenameUtils.getExtension(originalName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileException("Extension de fichier non autorisée : ." + extension + ". Formats acceptés : PDF, JPG, JPEG, PNG (RG6).");
        }

        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidFileException("Type MIME non autorisé : " + contentType);
        }
    }

    private Dossier getDossierAndCheckAccess(UUID dossierId, Utilisateur user) {
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier introuvable : " + dossierId));

        if (user.getRole() == RoleUtilisateur.ADMINISTRATEUR) {
            return dossier;
        }

        if (user.getRole() == RoleUtilisateur.ENTREPRENEUR && !dossier.getEntrepreneur().getId().equals(user.getId())) {
            throw new UnauthorizedDossierAccessException("Accès refusé : vous n'êtes pas propriétaire de ce dossier (RG13).");
        }

        return dossier;
    }

    private DocumentResponseDto mapToDto(DocumentEntity d) {
        String typeLabel = d.getDocumentTypeCode();
        try {
            DocumentType dt = DocumentType.valueOf(d.getDocumentTypeCode());
            typeLabel = dt.getLabel();
        } catch (Exception ignored) {}

        String contentUrl = "/dossiers/" + d.getDossier().getId() + "/documents/" + d.getId() + "/content";

        return new DocumentResponseDto(
                d.getId(),
                d.getDocumentTypeCode(),
                typeLabel,
                d.getOriginalFileName(),
                d.getMimeType(),
                d.getFileSize(),
                d.getSource(),
                d.getUploadedAt(),
                d.getUploadedBy().getId(),
                d.getNote(),
                contentUrl
        );
    }
}
