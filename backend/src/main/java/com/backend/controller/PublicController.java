package com.backend.controller;

import com.backend.dto.publicinfo.*;
import com.backend.service.PublicService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/public")
public class PublicController {

    private final PublicService publicService;

    public PublicController(PublicService publicService) {
        this.publicService = publicService;
    }

    /**
     * GET /public/legal-forms
     * Lister les formes juridiques prises en charge au Maroc
     */
    @GetMapping("/legal-forms")
    public ResponseEntity<List<LegalFormDto>> getLegalForms() {
        return ResponseEntity.ok(publicService.getLegalForms());
    }

    /**
     * POST /public/assistant/recommend
     * Obtenir une recommandation indicative de forme juridique
     */
    @PostMapping("/assistant/recommend")
    public ResponseEntity<AssistantRecommendationDto> recommendLegalForm(@Valid @RequestBody AssistantRequestDto request) {
        return ResponseEntity.ok(publicService.recommendLegalForm(request));
    }

    /**
     * POST /public/simulations
     * Simuler le coût et le délai indicatifs d'un parcours de création
     */
    @PostMapping("/simulations")
    public ResponseEntity<SimulationDto> simulate(@Valid @RequestBody SimulationRequestDto request) {
        return ResponseEntity.ok(publicService.simulate(request));
    }

    /**
     * GET /public/faqs
     * Consulter la FAQ publique marocaine
     */
    @GetMapping("/faqs")
    public ResponseEntity<List<FaqItemDto>> getFaqs() {
        return ResponseEntity.ok(publicService.getFaqs());
    }
}
