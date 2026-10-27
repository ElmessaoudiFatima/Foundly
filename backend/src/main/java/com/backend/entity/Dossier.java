package com.backend.entity;

import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "dossier")
public class Dossier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @Column(length = 200)
    private String nomEntreprise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LegalFormCode formeJuridique;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DossierStatus statut;

    @Column(nullable = false, length = 100)
    private String region;

    @Column(length = 100)
    private String etapeActuelle;

    @Column(length = 50)
    private String currentStepCode;

    @Column
    private Integer currentStepOrder = 1;

    @Column(precision = 15, scale = 2)
    private BigDecimal capitalMad = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean capitalBlockageRequired = false;

    @Column(length = 1000)
    private String descriptionActivite;

    @Column(length = 500)
    private String adresseSiege;

    @Column(length = 1000)
    private String rejectionReason;

    @Column
    private UUID workflowVersionId;

    @Column
    private Instant submittedAt;

    @Column
    private Instant finalizedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entrepreneur_id", nullable = false)
    private Utilisateur entrepreneur;

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordre ASC")
    private List<SuiviEtape> steps = new ArrayList<>();

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("uploadedAt DESC")
    private List<DocumentEntity> documents = new ArrayList<>();

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<Paiement> paiements = new ArrayList<>();

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("occurredAt DESC")
    private List<AuditEvent> auditEvents = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public Dossier() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getNomEntreprise() { return nomEntreprise; }
    public void setNomEntreprise(String nomEntreprise) { this.nomEntreprise = nomEntreprise; }

    public LegalFormCode getFormeJuridique() { return formeJuridique; }
    public void setFormeJuridique(LegalFormCode formeJuridique) { this.formeJuridique = formeJuridique; }

    public DossierStatus getStatut() { return statut; }
    public void setStatut(DossierStatus statut) { this.statut = statut; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getEtapeActuelle() { return etapeActuelle; }
    public void setEtapeActuelle(String etapeActuelle) { this.etapeActuelle = etapeActuelle; }

    public String getCurrentStepCode() { return currentStepCode; }
    public void setCurrentStepCode(String currentStepCode) { this.currentStepCode = currentStepCode; }

    public Integer getCurrentStepOrder() { return currentStepOrder; }
    public void setCurrentStepOrder(Integer currentStepOrder) { this.currentStepOrder = currentStepOrder; }

    public BigDecimal getCapitalMad() { return capitalMad; }
    public void setCapitalMad(BigDecimal capitalMad) { this.capitalMad = capitalMad; }

    public boolean isCapitalBlockageRequired() { return capitalBlockageRequired; }
    public void setCapitalBlockageRequired(boolean capitalBlockageRequired) { this.capitalBlockageRequired = capitalBlockageRequired; }

    public String getDescriptionActivite() { return descriptionActivite; }
    public void setDescriptionActivite(String descriptionActivite) { this.descriptionActivite = descriptionActivite; }

    public String getAdresseSiege() { return adresseSiege; }
    public void setAdresseSiege(String adresseSiege) { this.adresseSiege = adresseSiege; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public UUID getWorkflowVersionId() { return workflowVersionId; }
    public void setWorkflowVersionId(UUID workflowVersionId) { this.workflowVersionId = workflowVersionId; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Instant finalizedAt) { this.finalizedAt = finalizedAt; }

    public Utilisateur getEntrepreneur() { return entrepreneur; }
    public void setEntrepreneur(Utilisateur entrepreneur) { this.entrepreneur = entrepreneur; }

    public List<SuiviEtape> getSteps() { return steps; }
    public void setSteps(List<SuiviEtape> steps) { this.steps = steps; }

    public List<DocumentEntity> getDocuments() { return documents; }
    public void setDocuments(List<DocumentEntity> documents) { this.documents = documents; }

    public List<Paiement> getPaiements() { return paiements; }
    public void setPaiements(List<Paiement> paiements) { this.paiements = paiements; }

    public List<AuditEvent> getAuditEvents() { return auditEvents; }
    public void setAuditEvents(List<AuditEvent> auditEvents) { this.auditEvents = auditEvents; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
