package com.backend.entity;

import com.backend.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "paiement")
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suivi_etape_id")
    private SuiviEtape suiviEtape;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montantMad;

    @Column(nullable = false, unique = true, length = 50)
    private String referenceSimulation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus statut = PaymentStatus.EN_ATTENTE;

    @Column(length = 50)
    private String methode = "CARTE_BANCAIRE_SIMULEE";

    @Column(length = 20)
    private String numeroCarteMasque;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @Column
    private Instant confirmedAt;

    public Paiement() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Dossier getDossier() { return dossier; }
    public void setDossier(Dossier dossier) { this.dossier = dossier; }

    public SuiviEtape getSuiviEtape() { return suiviEtape; }
    public void setSuiviEtape(SuiviEtape suiviEtape) { this.suiviEtape = suiviEtape; }

    public BigDecimal getMontantMad() { return montantMad; }
    public void setMontantMad(BigDecimal montantMad) { this.montantMad = montantMad; }

    public String getReferenceSimulation() { return referenceSimulation; }
    public void setReferenceSimulation(String referenceSimulation) { this.referenceSimulation = referenceSimulation; }

    public PaymentStatus getStatut() { return statut; }
    public void setStatut(PaymentStatus statut) { this.statut = statut; }

    public String getMethode() { return methode; }
    public void setMethode(String methode) { this.methode = methode; }

    public String getNumeroCarteMasque() { return numeroCarteMasque; }
    public void setNumeroCarteMasque(String numeroCarteMasque) { this.numeroCarteMasque = numeroCarteMasque; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
