package com.backend.service.impl;

import com.backend.dto.workflow.*;
import com.backend.entity.EtapeWorkflow;
import com.backend.entity.WorkflowVersion;
import com.backend.enums.LegalFormCode;
import com.backend.enums.WorkflowStatus;
import com.backend.exception.WorkflowVersionNotEditableException;
import com.backend.exception.WorkflowVersionNotFoundException;
import com.backend.repository.EtapeWorkflowRepository;
import com.backend.repository.WorkflowVersionRepository;
import com.backend.service.WorkflowVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class WorkflowVersionServiceImpl implements WorkflowVersionService {

    private final WorkflowVersionRepository workflowVersionRepository;
    private final EtapeWorkflowRepository etapeWorkflowRepository;

    public WorkflowVersionServiceImpl(WorkflowVersionRepository workflowVersionRepository,
                                      EtapeWorkflowRepository etapeWorkflowRepository) {
        this.workflowVersionRepository = workflowVersionRepository;
        this.etapeWorkflowRepository = etapeWorkflowRepository;
    }

    // -----------------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowVersionSummaryDto> getAllVersions(LegalFormCode formeJuridique) {
        List<WorkflowVersion> versions;
        if (formeJuridique != null) {
            versions = workflowVersionRepository.findByFormeJuridiqueOrderByDateModificationDesc(formeJuridique);
        } else {
            versions = workflowVersionRepository.findAllByOrderByFormeJuridiqueAscDateModificationDesc();
        }
        return versions.stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowVersionDetailDto getVersionById(UUID id) {
        WorkflowVersion version = findOrThrow(id);
        return toDetailDto(version);
    }

    // -----------------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------------

    @Override
    public WorkflowVersionDetailDto createVersion(CreateWorkflowVersionRequest request) {
        LegalFormCode forme = request.getFormeJuridique();
        String nextVersion = computeNextVersion(forme, request.getDupliquerDepuis());

        WorkflowVersion version = new WorkflowVersion();
        version.setFormeJuridique(forme);
        version.setNumeroVersion(nextVersion);
        version.setStatut(WorkflowStatus.BROUILLON);
        version.setDescription(request.getDescription());

        WorkflowVersion saved = workflowVersionRepository.save(version);

        // Copier les Ã©tapes si dupliquerDepuis est fourni
        if (request.getDupliquerDepuis() != null) {
            WorkflowVersion source = findOrThrow(request.getDupliquerDepuis());
            List<EtapeWorkflow> sourceEtapes = etapeWorkflowRepository
                    .findByWorkflowVersionIdOrderByOrdreAsc(source.getId());
            for (EtapeWorkflow src : sourceEtapes) {
                EtapeWorkflow copy = new EtapeWorkflow();
                copy.setOrdre(src.getOrdre());
                copy.setCode(src.getCode());
                copy.setLibelle(src.getLibelle());
                copy.setOrganismeCode(src.getOrganismeCode());
                copy.setOrganismeLibelle(src.getOrganismeLibelle());
                copy.setFraisMad(src.getFraisMad());
                copy.setDelaiIndicatif(src.getDelaiIndicatif());
                copy.setConditionApplication(src.getConditionApplication());
                copy.setObligatoire(src.isObligatoire());
                copy.setExterne(src.isExterne());
                copy.setWorkflowVersion(saved);
                etapeWorkflowRepository.save(copy);
            }
        }

        // Recharger pour avoir les Ã©tapes
        return toDetailDto(findOrThrow(saved.getId()));
    }

    // -----------------------------------------------------------------------
    // PUBLISH / ARCHIVE
    // -----------------------------------------------------------------------

    @Override
    public WorkflowVersionDetailDto publishVersion(UUID id) {
        WorkflowVersion version = findOrThrow(id);

        if (version.getStatut() != WorkflowStatus.BROUILLON) {
            throw new WorkflowVersionNotEditableException(
                    "Seule une version BROUILLON peut Ãªtre publiÃ©e. Statut actuel : " + version.getStatut());
        }

        // Valider : au moins 1 Ã©tape obligatoire
        List<EtapeWorkflow> etapes = etapeWorkflowRepository
                .findByWorkflowVersionIdOrderByOrdreAsc(version.getId());
        boolean hasObligatoire = etapes.stream().anyMatch(EtapeWorkflow::isObligatoire);
        if (!hasObligatoire) {
            throw new IllegalStateException(
                    "Impossible de publier une version sans etape obligatoire.");
        }

        // Archiver l'ancienne version ACTIVE si elle existe
        Optional<WorkflowVersion> oldActive = workflowVersionRepository
                .findByFormeJuridiqueAndStatut(version.getFormeJuridique(), WorkflowStatus.ACTIVE);
        oldActive.ifPresent(old -> {
            old.setStatut(WorkflowStatus.ARCHIVEE);
            workflowVersionRepository.save(old);
        });

        version.setStatut(WorkflowStatus.ACTIVE);
        workflowVersionRepository.save(version);

        return toDetailDto(findOrThrow(id));
    }

    @Override
    public WorkflowVersionDetailDto archiverVersion(UUID id) {
        WorkflowVersion version = findOrThrow(id);

        if (version.getStatut() != WorkflowStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Seule une version ACTIVE peut Ãªtre archivÃ©e. Statut actuel : " + version.getStatut());
        }

        version.setStatut(WorkflowStatus.ARCHIVEE);
        workflowVersionRepository.save(version);

        return toDetailDto(findOrThrow(id));
    }

    // -----------------------------------------------------------------------
    // ETAPES CRUD
    // -----------------------------------------------------------------------

    @Override
    public EtapeWorkflowDto addEtape(UUID versionId, AddEtapeRequest request) {
        WorkflowVersion version = findOrThrow(versionId);
        assertEditable(version);

        List<EtapeWorkflow> existing = etapeWorkflowRepository
                .findByWorkflowVersionIdOrderByOrdreAsc(versionId);
        int nextOrdre = existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getOrdre() + 1;

        EtapeWorkflow etape = new EtapeWorkflow();
        etape.setOrdre(nextOrdre);
        etape.setCode(request.getCode());
        etape.setLibelle(request.getLibelle());
        etape.setOrganismeCode(request.getOrganismeCode());
        etape.setOrganismeLibelle(request.getOrganismeLibelle());
        etape.setFraisMad(request.getFraisMad() != null ? request.getFraisMad() : BigDecimal.ZERO);
        etape.setDelaiIndicatif(request.getDelaiIndicatif());
        etape.setConditionApplication(request.getConditionApplication());
        etape.setObligatoire(request.isObligatoire());
        etape.setExterne(request.isExterne());
        etape.setWorkflowVersion(version);

        EtapeWorkflow saved = etapeWorkflowRepository.save(etape);
        return toEtapeDto(saved);
    }

    @Override
    public EtapeWorkflowDto updateEtape(UUID versionId, UUID etapeId, UpdateEtapeRequest request) {
        WorkflowVersion version = findOrThrow(versionId);
        assertEditable(version);

        EtapeWorkflow etape = etapeWorkflowRepository.findById(etapeId)
                .orElseThrow(() -> new WorkflowVersionNotFoundException("Ã‰tape introuvable : " + etapeId));

        if (!etape.getWorkflowVersion().getId().equals(versionId)) {
            throw new WorkflowVersionNotFoundException(
                    "L'Ã©tape " + etapeId + " n'appartient pas Ã  la version " + versionId);
        }

        etape.setLibelle(request.getLibelle());
        etape.setOrganismeCode(request.getOrganismeCode());
        etape.setOrganismeLibelle(request.getOrganismeLibelle());
        etape.setFraisMad(request.getFraisMad() != null ? request.getFraisMad() : BigDecimal.ZERO);
        etape.setDelaiIndicatif(request.getDelaiIndicatif());
        etape.setConditionApplication(request.getConditionApplication());
        etape.setObligatoire(request.isObligatoire());
        etape.setExterne(request.isExterne());

        EtapeWorkflow saved = etapeWorkflowRepository.save(etape);
        return toEtapeDto(saved);
    }

    @Override
    public void deleteEtape(UUID versionId, UUID etapeId) {
        WorkflowVersion version = findOrThrow(versionId);
        assertEditable(version);

        EtapeWorkflow etape = etapeWorkflowRepository.findById(etapeId)
                .orElseThrow(() -> new WorkflowVersionNotFoundException("Ã‰tape introuvable : " + etapeId));

        if (!etape.getWorkflowVersion().getId().equals(versionId)) {
            throw new WorkflowVersionNotFoundException(
                    "L'Ã©tape " + etapeId + " n'appartient pas Ã  la version " + versionId);
        }

        etapeWorkflowRepository.delete(etape);
    }

    @Override
    public WorkflowVersionDetailDto reorderEtapes(UUID versionId, ReorderEtapesRequest request) {
        WorkflowVersion version = findOrThrow(versionId);
        assertEditable(version);

        List<UUID> orderedIds = request.getEtapeIds();
        for (int i = 0; i < orderedIds.size(); i++) {
            UUID etapeId = orderedIds.get(i);
            EtapeWorkflow etape = etapeWorkflowRepository.findById(etapeId)
                    .orElseThrow(() -> new WorkflowVersionNotFoundException("Ã‰tape introuvable : " + etapeId));
            etape.setOrdre(i + 1);
            etapeWorkflowRepository.save(etape);
        }

        return toDetailDto(findOrThrow(versionId));
    }

    // -----------------------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------------------

    private WorkflowVersion findOrThrow(UUID id) {
        return workflowVersionRepository.findById(id)
                .orElseThrow(() -> new WorkflowVersionNotFoundException(id));
    }

    private void assertEditable(WorkflowVersion version) {
        if (version.getStatut() != WorkflowStatus.BROUILLON) {
            throw new WorkflowVersionNotEditableException(version.getId());
        }
    }

    /**
     * Calcule le prochain numÃ©ro de version pour une forme juridique donnÃ©e.
     * IncrÃ©mente le chiffre avant le premier point (ex. "1.0" -> "2.0").
     */
    private String computeNextVersion(LegalFormCode forme, UUID dupliquerDepuis) {
        List<WorkflowVersion> existing = workflowVersionRepository
                .findByFormeJuridiqueOrderByDateModificationDesc(forme);

        if (existing.isEmpty()) {
            return "1.0";
        }

        // Trouver le numÃ©ro max
        int max = 0;
        for (WorkflowVersion v : existing) {
            try {
                String num = v.getNumeroVersion();
                int major = Integer.parseInt(num.split("\\.")[0]);
                if (major > max) max = major;
            } catch (Exception ignored) {}
        }

        return (max + 1) + ".0";
    }

    // -----------------------------------------------------------------------
    // MAPPING
    // -----------------------------------------------------------------------

    private WorkflowVersionSummaryDto toSummaryDto(WorkflowVersion v) {
        WorkflowVersionSummaryDto dto = new WorkflowVersionSummaryDto();
        dto.setId(v.getId());
        dto.setFormeJuridique(v.getFormeJuridique().name());
        dto.setFormeJuridiqueLabel(labelForForme(v.getFormeJuridique()));
        dto.setNumeroVersion(v.getNumeroVersion());
        dto.setStatut(v.getStatut() != null ? v.getStatut().name() : null);
        dto.setStatutLabel(labelForStatut(v.getStatut()));
        dto.setNombreEtapes(v.getEtapes() != null ? v.getEtapes().size() : 0);
        dto.setDateModification(v.getDateModification() != null ? v.getDateModification().toString() : null);
        return dto;
    }

    private WorkflowVersionDetailDto toDetailDto(WorkflowVersion v) {
        WorkflowVersionDetailDto dto = new WorkflowVersionDetailDto();
        dto.setId(v.getId());
        dto.setFormeJuridique(v.getFormeJuridique().name());
        dto.setFormeJuridiqueLabel(labelForForme(v.getFormeJuridique()));
        dto.setNumeroVersion(v.getNumeroVersion());
        dto.setStatut(v.getStatut() != null ? v.getStatut().name() : null);
        dto.setStatutLabel(labelForStatut(v.getStatut()));
        dto.setDescription(v.getDescription());
        dto.setDateModification(v.getDateModification() != null ? v.getDateModification().toString() : null);
        dto.setDateCreation(v.getDateCreation() != null ? v.getDateCreation().toString() : null);

        List<EtapeWorkflow> etapes = etapeWorkflowRepository.findByWorkflowVersionIdOrderByOrdreAsc(v.getId());
        dto.setEtapes(etapes.stream().map(this::toEtapeDto).collect(Collectors.toList()));
        return dto;
    }

    private EtapeWorkflowDto toEtapeDto(EtapeWorkflow e) {
        EtapeWorkflowDto dto = new EtapeWorkflowDto();
        dto.setId(e.getId());
        dto.setOrdre(e.getOrdre());
        dto.setCode(e.getCode());
        dto.setLibelle(e.getLibelle());
        dto.setOrganismeCode(e.getOrganismeCode());
        dto.setOrganismeLibelle(e.getOrganismeLibelle());
        dto.setFraisMad(e.getFraisMad());
        dto.setDelaiIndicatif(e.getDelaiIndicatif());
        dto.setConditionApplication(e.getConditionApplication());
        dto.setObligatoire(e.isObligatoire());
        dto.setExterne(e.isExterne());
        return dto;
    }

    public static String labelForForme(LegalFormCode code) {
        if (code == null) return null;
        switch (code) {
            case AUTO_ENTREPRENEUR: return "Auto-entrepreneur";
            case SARL:              return "SARL";
            case SARL_AU:           return "SARL-AU";
            case SA:                return "SA";
            default:                return code.name();
        }
    }

    public static String labelForStatut(WorkflowStatus statut) {
        if (statut == null) return null;
        switch (statut) {
            case BROUILLON: return "Brouillon";
            case ACTIVE:    return "Active";
            case ARCHIVEE:  return "Archivée";
            default:        return statut.name();
        }
    }
}
