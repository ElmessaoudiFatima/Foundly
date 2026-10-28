package com.backend.entity;

import com.backend.enums.LegalFormCode;
import com.backend.enums.WorkflowStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "workflow_version",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_workflow_forme_version",
        columnNames = {"forme_juridique", "numero_version"}
    )
)
public class WorkflowVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "forme_juridique", nullable = false, length = 30)
    private LegalFormCode formeJuridique;

    @Column(name = "numero_version", nullable = false, length = 20)
    private String numeroVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", length = 20)
    private WorkflowStatus statut;

    @UpdateTimestamp
    @Column(name = "date_modification")
    private Instant dateModification;

    @CreationTimestamp
    @Column(name = "date_creation", updatable = false)
    private Instant dateCreation;

    @Column(name = "description", length = 500)
    private String description;

    @OneToMany(mappedBy = "workflowVersion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordre ASC")
    private List<EtapeWorkflow> etapes = new ArrayList<>();

    public WorkflowVersion() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LegalFormCode getFormeJuridique() { return formeJuridique; }
    public void setFormeJuridique(LegalFormCode formeJuridique) { this.formeJuridique = formeJuridique; }

    public String getNumeroVersion() { return numeroVersion; }
    public void setNumeroVersion(String numeroVersion) { this.numeroVersion = numeroVersion; }

    public WorkflowStatus getStatut() { return statut; }
    public void setStatut(WorkflowStatus statut) { this.statut = statut; }

    public Instant getDateModification() { return dateModification; }
    public void setDateModification(Instant dateModification) { this.dateModification = dateModification; }

    public Instant getDateCreation() { return dateCreation; }
    public void setDateCreation(Instant dateCreation) { this.dateCreation = dateCreation; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<EtapeWorkflow> getEtapes() { return etapes; }
    public void setEtapes(List<EtapeWorkflow> etapes) { this.etapes = etapes; }
}
