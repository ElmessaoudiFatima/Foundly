package com.backend.controller;

import com.backend.dto.request.OrganizationCreateRequest;
import com.backend.dto.request.OrganizationUpdateRequest;
import com.backend.dto.response.OrganizationResponse;
import com.backend.service.OrganismeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur d'administration des organismes.
 * Pur délégateur : aucune logique, aucune entité, aucun repository.
 * Sécurité : /admin/** requiert hasRole("ADMINISTRATEUR") — défini dans SecurityConfig.
 */
@RestController
@RequestMapping("/admin/organizations")
public class AdminOrganizationController {

    private final OrganismeService organismeService;

    public AdminOrganizationController(OrganismeService organismeService) {
        this.organismeService = organismeService;
    }

    /**
     * GET /admin/organizations
     * Liste tous les organismes (triés par code ASC) avec leur nombre d'agents actifs.
     */
    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> listOrganizations() {
        return ResponseEntity.ok(organismeService.listOrganizations());
    }

    /**
     * POST /admin/organizations
     * Crée un nouvel organisme. Répond 201 Created.
     */
    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody OrganizationCreateRequest request) {
        OrganizationResponse response = organismeService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PATCH /admin/organizations/{organizationCode}
     * Met à jour le nom et/ou la description (le code est immuable).
     */
    @PatchMapping("/{organizationCode}")
    public ResponseEntity<OrganizationResponse> updateOrganization(
            @PathVariable String organizationCode,
            @Valid @RequestBody(required = false) OrganizationUpdateRequest request) {
        OrganizationResponse response = organismeService.updateOrganization(organizationCode, request);
        return ResponseEntity.ok(response);
    }
}
