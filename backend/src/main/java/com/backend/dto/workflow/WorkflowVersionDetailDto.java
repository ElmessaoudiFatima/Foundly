package com.backend.dto.workflow;

import java.util.List;
import java.util.UUID;

public class WorkflowVersionDetailDto {

    private UUID id;
    private String formeJuridique;
    private String formeJuridiqueLabel;
    private String numeroVersion;
    private String statut;
    private String statutLabel;
    private String description;
    private String dateModification;
    private String dateCreation;
    private List<EtapeWorkflowDto> etapes;

    public WorkflowVersionDetailDto() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFormeJuridique() { return formeJuridique; }
    public void setFormeJuridique(String formeJuridique) { this.formeJuridique = formeJuridique; }

    public String getFormeJuridiqueLabel() { return formeJuridiqueLabel; }
    public void setFormeJuridiqueLabel(String formeJuridiqueLabel) { this.formeJuridiqueLabel = formeJuridiqueLabel; }

    public String getNumeroVersion() { return numeroVersion; }
    public void setNumeroVersion(String numeroVersion) { this.numeroVersion = numeroVersion; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getStatutLabel() { return statutLabel; }
    public void setStatutLabel(String statutLabel) { this.statutLabel = statutLabel; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDateModification() { return dateModification; }
    public void setDateModification(String dateModification) { this.dateModification = dateModification; }

    public String getDateCreation() { return dateCreation; }
    public void setDateCreation(String dateCreation) { this.dateCreation = dateCreation; }

    public List<EtapeWorkflowDto> getEtapes() { return etapes; }
    public void setEtapes(List<EtapeWorkflowDto> etapes) { this.etapes = etapes; }
}
