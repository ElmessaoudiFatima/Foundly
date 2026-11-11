package com.backend.entity;

import com.backend.enums.ActorType;
import com.backend.enums.StepStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suivi_etape")
public class SuiviEtape {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @Column(nullable = false, length = 50)
    private String etapeCode;

    @Column(nullable = false, length = 200)
    private String libelle;

    @Column(nullable = false)
    private Integer ordre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActorType actionOwner;

    @Column(length = 50)
    private String organismeCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StepStatus statut;

    @Column(precision = 10, scale = 2)
    private BigDecimal fraisMad = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean paymentRequired = false;

    @Column
    private Integer indicativeDurationDays = 2;

    @Column
    private Instant startedAt;

    @Column
    private Instant decidedAt;

    @Column(length = 1000)
    private String motifRejet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Utilisateur agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_by_agent_id")
    private Utilisateur claimedByAgent;

    @Column
    private Instant claimedAt;

    @Column(columnDefinition = "TEXT")
    private String checklistReviewJson;

    @Column(columnDefinition = "TEXT")
    private String validationResultJson;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public SuiviEtape() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Dossier getDossier() { return dossier; }
    public void setDossier(Dossier dossier) { this.dossier = dossier; }

    public String getEtapeCode() { return etapeCode; }
    public void setEtapeCode(String etapeCode) { this.etapeCode = etapeCode; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }

    public ActorType getActionOwner() { return actionOwner; }
    public void setActionOwner(ActorType actionOwner) { this.actionOwner = actionOwner; }

    public String getOrganismeCode() { return organismeCode; }
    public void setOrganismeCode(String organismeCode) { this.organismeCode = organismeCode; }

    public StepStatus getStatut() { return statut; }
    public void setStatut(StepStatus statut) { this.statut = statut; }

    public BigDecimal getFraisMad() { return fraisMad; }
    public void setFraisMad(BigDecimal fraisMad) { this.fraisMad = fraisMad; }

    public boolean isPaymentRequired() { return paymentRequired; }
    public void setPaymentRequired(boolean paymentRequired) { this.paymentRequired = paymentRequired; }

    public Integer getIndicativeDurationDays() { return indicativeDurationDays; }
    public void setIndicativeDurationDays(Integer indicativeDurationDays) { this.indicativeDurationDays = indicativeDurationDays; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getMotifRejet() { return motifRejet; }
    public void setMotifRejet(String motifRejet) { this.motifRejet = motifRejet; }

    public Utilisateur getAgent() { return agent; }
    public void setAgent(Utilisateur agent) { this.agent = agent; }

    public Utilisateur getClaimedByAgent() { return claimedByAgent; }
    public void setClaimedByAgent(Utilisateur claimedByAgent) { this.claimedByAgent = claimedByAgent; }

    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }

    public String getChecklistReviewJson() { return checklistReviewJson; }
    public void setChecklistReviewJson(String checklistReviewJson) { this.checklistReviewJson = checklistReviewJson; }

    public String getValidationResultJson() { return validationResultJson; }
    public void setValidationResultJson(String validationResultJson) { this.validationResultJson = validationResultJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
