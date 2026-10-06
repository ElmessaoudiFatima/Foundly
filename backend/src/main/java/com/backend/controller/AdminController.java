package com.backend.controller;

import com.backend.dto.admin.*;
import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;
import com.backend.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * GET /admin/dashboard
     * Statistiques de pilotage de la plateforme
     */
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String region
    ) {
        AdminDashboardResponse res = adminService.getDashboardStats(period, region);
        return ResponseEntity.ok(res);
    }

    /**
     * GET /admin/dossiers
     * Tous les dossiers avec filtres et pagination
     */
    @GetMapping("/dossiers")
    public ResponseEntity<PagedDossierResponse> getDossiers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DossierStatus status,
            @RequestParam(required = false) LegalFormCode legalForm,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String currentStep,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PagedDossierResponse res = adminService.getDossiers(search, status, legalForm, region, currentStep, page, size);
        return ResponseEntity.ok(res);
    }

    /**
     * GET /admin/dossiers/export
     * Exporter les dossiers au format CSV
     */
    @GetMapping(value = "/dossiers/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportDossiers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DossierStatus status,
            @RequestParam(required = false) LegalFormCode legalForm,
            @RequestParam(required = false) String region
    ) {
        byte[] csv = adminService.exportDossiersCsv(search, status, legalForm, region);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dossiers-foundly.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }

    /**
     * GET /admin/agents
     * Liste des comptes agents
     */
    @GetMapping("/agents")
    public ResponseEntity<PagedAgentResponse> getAgents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PagedAgentResponse res = adminService.getAgents(search, organization, active, page, size);
        return ResponseEntity.ok(res);
    }

    /**
     * POST /admin/agents
     * Création d'un nouvel agent rattaché à un organisme
     */
    @PostMapping("/agents")
    public ResponseEntity<AgentAdminResponse> createAgent(@Valid @RequestBody AgentCreateAdminRequest request) {
        AgentAdminResponse res = adminService.createAgent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /**
     * PATCH /admin/agents/{id}/status
     * Activation / Désactivation d'un compte agent
     */
    @PatchMapping("/agents/{id}/status")
    public ResponseEntity<AgentAdminResponse> updateAgentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AgentUpdateStatusRequest request
    ) {
        AgentAdminResponse res = adminService.updateAgentStatus(id, request.active());
        return ResponseEntity.ok(res);
    }
}
