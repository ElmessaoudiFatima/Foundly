package com.backend.service;

import com.backend.dto.workflow.*;
import com.backend.enums.LegalFormCode;

import java.util.List;
import java.util.UUID;

public interface WorkflowVersionService {

    List<WorkflowVersionSummaryDto> getAllVersions(LegalFormCode formeJuridique);

    WorkflowVersionDetailDto getVersionById(UUID id);

    WorkflowVersionDetailDto createVersion(CreateWorkflowVersionRequest request);

    /**
     * BROUILLON -> ACTIVE ; archive l'ancienne ACTIVE pour la même forme juridique.
     */
    WorkflowVersionDetailDto publishVersion(UUID id);

    /**
     * ACTIVE -> ARCHIVEE.
     */
    WorkflowVersionDetailDto archiverVersion(UUID id);

    EtapeWorkflowDto addEtape(UUID versionId, AddEtapeRequest request);

    EtapeWorkflowDto updateEtape(UUID versionId, UUID etapeId, UpdateEtapeRequest request);

    void deleteEtape(UUID versionId, UUID etapeId);

    WorkflowVersionDetailDto reorderEtapes(UUID versionId, ReorderEtapesRequest request);
}
