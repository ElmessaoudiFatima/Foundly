package com.backend.dto.workflow;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public class UpdateEtapeRequest {

    @NotBlank
    private String libelle;

    private String organismeCode;
    private String organismeLibelle;
    private BigDecimal fraisMad;
    private String delaiIndicatif;
    private String conditionApplication;
    private boolean obligatoire;
    private boolean externe;

    public UpdateEtapeRequest() {}

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getOrganismeCode() { return organismeCode; }
    public void setOrganismeCode(String organismeCode) { this.organismeCode = organismeCode; }

    public String getOrganismeLibelle() { return organismeLibelle; }
    public void setOrganismeLibelle(String organismeLibelle) { this.organismeLibelle = organismeLibelle; }

    public BigDecimal getFraisMad() { return fraisMad; }
    public void setFraisMad(BigDecimal fraisMad) { this.fraisMad = fraisMad; }

    public String getDelaiIndicatif() { return delaiIndicatif; }
    public void setDelaiIndicatif(String delaiIndicatif) { this.delaiIndicatif = delaiIndicatif; }

    public String getConditionApplication() { return conditionApplication; }
    public void setConditionApplication(String conditionApplication) { this.conditionApplication = conditionApplication; }

    public boolean isObligatoire() { return obligatoire; }
    public void setObligatoire(boolean obligatoire) { this.obligatoire = obligatoire; }

    public boolean isExterne() { return externe; }
    public void setExterne(boolean externe) { this.externe = externe; }
}
