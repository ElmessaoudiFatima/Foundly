package com.backend.service;

import com.backend.dto.agent.AgentQueueItemDto;
import com.backend.dto.agent.AgentValidateRequest;
import com.backend.entity.AuditEvent;
import com.backend.entity.Dossier;
import com.backend.entity.SuiviEtape;
import com.backend.entity.Utilisateur;
import com.backend.enums.ActorType;
import com.backend.enums.AuditEventAction;
import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;
import com.backend.enums.StepStatus;
import com.backend.exception.AgentClaimConflictException;
import com.backend.exception.UnauthorizedDossierAccessException;
import com.backend.repository.DossierRepository;
import com.backend.repository.AuditEventRepository;
import com.backend.repository.DocumentRepository;
import com.backend.repository.SuiviEtapeRepository;
import com.backend.service.impl.AgentServiceImpl;
import com.backend.service.WorkflowEngineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @Mock
    private DossierRepository dossierRepository;
    @Mock
    private SuiviEtapeRepository suiviEtapeRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private WorkflowEngineService workflowEngineService;
    @InjectMocks
    private AgentServiceImpl agentService;

    @Test
    void queueOnlyReturnsAgentStepsAssignedToTheAgentsOrganization() {
        Utilisateur agent = agent("OMPIC");
        SuiviEtape matchingStep = step("OMPIC", ActorType.AGENT);
        SuiviEtape anotherOrganization = step("CRI", ActorType.AGENT);
        SuiviEtape entrepreneurStep = step("OMPIC", ActorType.ENTREPRENEUR);
        when(suiviEtapeRepository.findActiveStepsWithDossier(StepStatus.EN_COURS))
                .thenReturn(List.of(matchingStep, anotherOrganization, entrepreneurStep));

        List<AgentQueueItemDto> queue = agentService.getQueue(agent);

        assertEquals(1, queue.size());
        assertEquals("Casablanca-Settat", queue.get(0).region());
        assertEquals(matchingStep.getStartedAt().plusSeconds(2 * 24 * 60 * 60), queue.get(0).deadlineAt());
        assertFalse(queue.get(0).isResubmitted());
    }

    @Test
    void claimIsExclusiveAndCanBeRepeatedByItsOwner() {
        Utilisateur firstAgent = agent("OMPIC");
        Utilisateur secondAgent = agent("OMPIC");
        SuiviEtape step = step("OMPIC", ActorType.AGENT);
        when(suiviEtapeRepository.findFirstByDossierIdAndStatutOrderByOrdreAsc(
                step.getDossier().getId(), StepStatus.EN_COURS)).thenReturn(Optional.of(step));
        when(suiviEtapeRepository.save(any(SuiviEtape.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AgentQueueItemDto firstClaim = agentService.takeInCharge(step.getDossier().getId(), firstAgent);
        AgentQueueItemDto repeatedClaim = agentService.takeInCharge(step.getDossier().getId(), firstAgent);

        assertTrue(firstClaim.claimedByCurrentAgent());
        assertTrue(repeatedClaim.claimedByCurrentAgent());
        assertNotNull(step.getClaimedAt());
        assertThrows(AgentClaimConflictException.class,
                () -> agentService.takeInCharge(step.getDossier().getId(), secondAgent));
        verify(suiviEtapeRepository, times(1)).save(step);
    }

    @Test
    void accessIsDeniedWhenCurrentStepBelongsToAnotherOrganization() {
        Utilisateur agent = agent("OMPIC");
        SuiviEtape currentStep = step("CRI", ActorType.AGENT);
        when(suiviEtapeRepository.findFirstByDossierIdAndStatut(
                currentStep.getDossier().getId(), StepStatus.EN_COURS))
                .thenReturn(Optional.of(currentStep));
        assertThrows(UnauthorizedDossierAccessException.class,
                () -> agentService.assertDossierAccess(currentStep.getDossier().getId(), agent));
    }

    @Test
    void agentCanReadDossierClaimedByAnotherAgentOfSameOrganization() {
        Utilisateur agent = agent("OMPIC");
        SuiviEtape currentStep = step("OMPIC", ActorType.AGENT);
        currentStep.setClaimedByAgent(agent("OMPIC"));
        when(suiviEtapeRepository.findFirstByDossierIdAndStatut(
                currentStep.getDossier().getId(), StepStatus.EN_COURS))
                .thenReturn(Optional.of(currentStep));

        assertDoesNotThrow(() -> agentService.assertDossierAccess(currentStep.getDossier().getId(), agent));
    }

    @Test
    void agentCannotDecideWhenAnotherAgentOwnsTheClaim() {
        Utilisateur agent = agent("OMPIC");
        SuiviEtape currentStep = step("OMPIC", ActorType.AGENT);
        currentStep.setClaimedByAgent(agent("OMPIC"));
        when(suiviEtapeRepository.findFirstByDossierIdAndStatutOrderByOrdreAsc(
                currentStep.getDossier().getId(), StepStatus.EN_COURS))
                .thenReturn(Optional.of(currentStep));

        assertThrows(AgentClaimConflictException.class,
                () -> agentService.assertClaimedByAgent(currentStep.getDossier().getId(), agent));
    }

    @Test
    void validationRejectsIncompleteOrganizationChecklist() {
        Utilisateur agent = agent("OMPIC");
        SuiviEtape currentStep = step("OMPIC", ActorType.AGENT);
        currentStep.setClaimedByAgent(agent);
        when(dossierRepository.findById(currentStep.getDossier().getId()))
                .thenReturn(Optional.of(currentStep.getDossier()));
        when(suiviEtapeRepository.findFirstByDossierIdAndStatutOrderByOrdreAsc(
                currentStep.getDossier().getId(), StepStatus.EN_COURS))
                .thenReturn(Optional.of(currentStep));
        AgentValidateRequest request = new AgentValidateRequest(
                null,
                java.util.Map.of("documentsComplete", true),
                java.util.Map.of("reservedName", "Atlas", "issuedAt", "2026-01-01", "certificateNumber", "CN-1")
        );

        assertThrows(com.backend.exception.InvalidDossierStateException.class,
                () -> agentService.validateStep(currentStep.getDossier().getId(), request, agent));
        verify(documentRepository, never()).findByDossierIdOrderByUploadedAtDesc(any());
    }

    @Test
    void historyUsesImmutableDecisionAndStartTimesAfterStepIsResubmitted() {
        Utilisateur agent = agent("OMPIC");
        SuiviEtape step = step("OMPIC", ActorType.AGENT);
        step.setId(UUID.randomUUID());
        step.setStatut(StepStatus.EN_COURS);
        step.setStartedAt(Instant.parse("2026-02-01T10:00:00Z"));
        AuditEvent rejection = new AuditEvent(
                step.getDossier(),
                AuditEventAction.ETAPE_REJETEE,
                ActorType.AGENT,
                agent,
                "Agent Test",
                step.getId(),
                "Pièce manquante",
                "Étape rejetée"
        );
        rejection.setId(UUID.randomUUID());
        rejection.setOccurredAt(Instant.parse("2026-01-03T10:00:00Z"));
        rejection.setStepStartedAt(Instant.parse("2026-01-01T10:00:00Z"));
        when(auditEventRepository.findByActorIdAndActionInOrderByOccurredAtDesc(
                agent.getId(), List.of(AuditEventAction.ETAPE_VALIDEE, AuditEventAction.ETAPE_REJETEE)))
                .thenReturn(List.of(rejection));
        when(suiviEtapeRepository.findById(step.getId())).thenReturn(Optional.of(step));

        List<com.backend.dto.agent.AgentHistoryItemDto> history = agentService.getHistory(agent);

        assertEquals(1, history.size());
        assertEquals(StepStatus.REJETEE, history.get(0).decisionPrise());
        assertEquals(Instant.parse("2026-01-03T10:00:00Z"), history.get(0).decidedAt());
        assertEquals(Instant.parse("2026-01-01T10:00:00Z"), history.get(0).startedAt());
        assertEquals("Pièce manquante", history.get(0).motifRejet());
    }

    private Utilisateur agent(String organization) {
        Utilisateur agent = new Utilisateur();
        agent.setId(UUID.randomUUID());
        agent.setPrenom("Agent");
        agent.setNom("Test");
        agent.setOrganisme(organization);
        return agent;
    }

    private SuiviEtape step(String organization, ActorType owner) {
        Dossier dossier = new Dossier();
        dossier.setId(UUID.randomUUID());
        dossier.setReference("FND-TEST");
        dossier.setNomEntreprise("Entreprise test");
        dossier.setFormeJuridique(LegalFormCode.SARL);
        dossier.setStatut(DossierStatus.EN_COURS);
        dossier.setRegion("Casablanca-Settat");

        SuiviEtape step = new SuiviEtape();
        step.setDossier(dossier);
        step.setEtapeCode("VERIFICATION");
        step.setLibelle("Vérification");
        step.setOrdre(1);
        step.setActionOwner(owner);
        step.setOrganismeCode(organization);
        step.setStatut(StepStatus.EN_COURS);
        step.setStartedAt(Instant.parse("2026-01-01T10:00:00Z"));
        step.setIndicativeDurationDays(2);
        return step;
    }
}
