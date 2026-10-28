package com.backend.controller;

import com.backend.dto.workflow.*;
import com.backend.enums.LegalFormCode;
import com.backend.service.WorkflowVersionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/workflows")
@CrossOrigin
public class WorkflowVersionController {

    private final WorkflowVersionService workflowVersionService;

    public WorkflowVersionController(WorkflowVersionService workflowVersionService) {
        this.workflowVersionService = workflowVersionService;
    }

    /**
     * GET /admin/workflows?formeJuridique=SARL
     */
    @GetMapping
    public ResponseEntity<List<WorkflowVersionSummaryDto>> getAllVersions(
            @RequestParam(required = false) LegalFormCode formeJuridique) {
        return ResponseEntity.ok(workflowVersionService.getAllVersions(formeJuridique));
    }

    /**
     * POST /admin/workflows
     */
    @PostMapping
    public ResponseEntity<WorkflowVersionDetailDto> createVersion(
            @RequestBody @Valid CreateWorkflowVersionRequest request) {
        WorkflowVersionDetailDto dto = workflowVersionService.createVersion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * GET /admin/workflows/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<WorkflowVersionDetailDto> getVersionById(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowVersionService.getVersionById(id));
    }

    /**
     * POST /admin/workflows/{id}/publish
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<WorkflowVersionDetailDto> publishVersion(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowVersionService.publishVersion(id));
    }

    /**
     * POST /admin/workflows/{id}/archive
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<WorkflowVersionDetailDto> archiverVersion(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowVersionService.archiverVersion(id));
    }

    /**
     * POST /admin/workflows/{id}/etapes
     */
    @PostMapping("/{id}/etapes")
    public ResponseEntity<EtapeWorkflowDto> addEtape(
            @PathVariable UUID id,
            @RequestBody @Valid AddEtapeRequest request) {
        EtapeWorkflowDto dto = workflowVersionService.addEtape(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * PUT /admin/workflows/{id}/etapes/{etapeId}
     */
    @PutMapping("/{id}/etapes/{etapeId}")
    public ResponseEntity<EtapeWorkflowDto> updateEtape(
            @PathVariable UUID id,
            @PathVariable UUID etapeId,
            @RequestBody @Valid UpdateEtapeRequest request) {
        return ResponseEntity.ok(workflowVersionService.updateEtape(id, etapeId, request));
    }

    /**
     * DELETE /admin/workflows/{id}/etapes/{etapeId}
     */
    @DeleteMapping("/{id}/etapes/{etapeId}")
    public ResponseEntity<Void> deleteEtape(
            @PathVariable UUID id,
            @PathVariable UUID etapeId) {
        workflowVersionService.deleteEtape(id, etapeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /admin/workflows/{id}/etapes/reorder
     */
    @PutMapping("/{id}/etapes/reorder")
    public ResponseEntity<WorkflowVersionDetailDto> reorderEtapes(
            @PathVariable UUID id,
            @RequestBody @Valid ReorderEtapesRequest request) {
        return ResponseEntity.ok(workflowVersionService.reorderEtapes(id, request));
    }
}
