package com.backend.service;

import com.backend.dto.publicinfo.*;

import java.util.List;

public interface PublicService {

    List<LegalFormDto> getLegalForms();

    AssistantRecommendationDto recommendLegalForm(AssistantRequestDto request);

    SimulationDto simulate(SimulationRequestDto request);

    List<FaqItemDto> getFaqs();
}
