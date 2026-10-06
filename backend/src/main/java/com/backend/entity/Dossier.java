package com.backend.entity;

import com.backend.enums.DossierStatus;
import com.backend.enums.LegalFormCode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
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

    @Column(precision = 15, scale = 2)
    private BigDecimal capitalMad;

    @Column(length = 1000)
    private String descriptionActivite;

    @Column(length = 500)
    private String adresseSiege;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entrepreneur_id", nullable = false)
    private Utilisateur entrepreneur;

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

    public BigDecimal getCapitalMad() { return capitalMad; }
    public void setCapitalMad(BigDecimal capitalMad) { this.capitalMad = capitalMad; }

    public String getDescriptionActivite() { return descriptionActivite; }
    public void setDescriptionActivite(String descriptionActivite) { this.descriptionActivite = descriptionActivite; }

    public String getAdresseSiege() { return adresseSiege; }
    public void setAdresseSiege(String adresseSiege) { this.adresseSiege = adresseSiege; }

    public Utilisateur getEntrepreneur() { return entrepreneur; }
    public void setEntrepreneur(Utilisateur entrepreneur) { this.entrepreneur = entrepreneur; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
