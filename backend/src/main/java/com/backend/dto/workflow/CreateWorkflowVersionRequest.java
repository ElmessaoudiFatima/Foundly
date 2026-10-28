package com.backend.dto.workflow;

import com.backend.enums.LegalFormCode;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class CreateWorkflowVersionRequest {

    @NotNull
    private LegalFormCode formeJuridique;

    private String description;

    private UUID dupliquerDepuis;

    public CreateWorkflowVersionRequest() {}

    public LegalFormCode getFormeJuridique() { return formeJuridique; }
    public void setFormeJuridique(LegalFormCode formeJuridique) { this.formeJuridique = formeJuridique; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getDupliquerDepuis() { return dupliquerDepuis; }
    public void setDupliquerDepuis(UUID dupliquerDepuis) { this.dupliquerDepuis = dupliquerDepuis; }
}
