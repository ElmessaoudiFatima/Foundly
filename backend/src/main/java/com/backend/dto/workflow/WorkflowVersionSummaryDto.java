package com.backend.dto.workflow;

import java.util.UUID;

public class WorkflowVersionSummaryDto {

    private UUID id;
    private String formeJuridique;
    private String formeJuridiqueLabel;
    private String numeroVersion;
    private String statut;
    private String statutLabel;
    private int nombreEtapes;
    private String dateModification;

    public WorkflowVersionSummaryDto() {}

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

    public int getNombreEtapes() { return nombreEtapes; }
    public void setNombreEtapes(int nombreEtapes) { this.nombreEtapes = nombreEtapes; }

    public String getDateModification() { return dateModification; }
    public void setDateModification(String dateModification) { this.dateModification = dateModification; }
}
