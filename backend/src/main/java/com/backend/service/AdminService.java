package com.backend.service;

import com.backend.dto.admin.*;
import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;

import java.util.UUID;

public interface AdminService {

    AdminDashboardResponse getDashboardStats(String period, String region);

    PagedDossierResponse getDossiers(
            String search,
            DossierStatus status,
            LegalFormCode legalForm,
            String region,
            String currentStep,
            int page,
            int size
    );

    PagedAgentResponse getAgents(
            String search,
            String organization,
            Boolean active,
            int page,
            int size
    );

    AgentAdminResponse createAgent(AgentCreateAdminRequest request);

    AgentAdminResponse updateAgentStatus(UUID agentId, boolean active, String reason);

    default AgentAdminResponse updateAgentStatus(UUID agentId, boolean active) {
        return updateAgentStatus(agentId, active, null);
    }

    byte[] exportDossiersCsv(
            String search,
            DossierStatus status,
            LegalFormCode legalForm,
            String region
    );
}
