package com.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "etape_workflow")
public class EtapeWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Integer ordre;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 250)
    private String libelle;

    @Column(name = "organisme_code", length = 30)
    private String organismeCode;

    @Column(name = "organisme_libelle", length = 200)
    private String organismeLibelle;

    @Column(name = "frais_mad", precision = 10, scale = 2)
    private BigDecimal fraisMad = BigDecimal.ZERO;

    @Column(name = "delai_indicatif", length = 100)
    private String delaiIndicatif;

    @Column(name = "condition_application", length = 500)
    private String conditionApplication;

    @Column(nullable = false)
    private boolean obligatoire = true;

    @Column(nullable = false)
    private boolean externe = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_version_id", nullable = false)
    private WorkflowVersion workflowVersion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public EtapeWorkflow() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

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

    public WorkflowVersion getWorkflowVersion() { return workflowVersion; }
    public void setWorkflowVersion(WorkflowVersion workflowVersion) { this.workflowVersion = workflowVersion; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
