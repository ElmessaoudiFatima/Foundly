package com.backend.dto.workflow;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public class ReorderEtapesRequest {

    @NotEmpty
    private List<UUID> etapeIds;

    public ReorderEtapesRequest() {}

    public List<UUID> getEtapeIds() { return etapeIds; }
    public void setEtapeIds(List<UUID> etapeIds) { this.etapeIds = etapeIds; }
}
