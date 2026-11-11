package com.backend.service;

import com.backend.entity.Dossier;
import com.backend.entity.SuiviEtape;
import com.backend.entity.Utilisateur;
import com.backend.enums.StepStatus;

import java.util.List;
import java.util.UUID;

public interface WorkflowEngineService {

    List<SuiviEtape> initializeStepsForDossier(Dossier dossier);

    void advanceWorkflow(Dossier dossier, String notes);

    void advanceWorkflow(Dossier dossier, String notes, Utilisateur decidingAgent);

    void rejectWorkflowStep(Dossier dossier, UUID stepTrackingId, String motifRejet, Utilisateur agent);

    void resumeWorkflowAfterCorrection(Dossier dossier);

    void checkAndAutoValidateCapitalBlockageIfUploaded(Dossier dossier);
}
