package com.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "associe")
public class Associe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(nullable = false, length = 30)
    private String cinOuPasseport;

    @Column(nullable = false, length = 50)
    private String nationalite;

    @Column(nullable = false, length = 255)
    private String adresse;

    @Column(nullable = false)
    private Long nombreParts;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal montantApport;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal pourcentageDetention;

    @Column(nullable = false)
    private boolean actif = true;

    public Associe() {}

    public Associe(Dossier dossier, String nom, String prenom, String cinOuPasseport,
                   String nationalite, String adresse, Long nombreParts,
                   BigDecimal montantApport, BigDecimal pourcentageDetention) {
        this.dossier = dossier;
        this.nom = nom;
        this.prenom = prenom;
        this.cinOuPasseport = cinOuPasseport;
        this.nationalite = nationalite;
        this.adresse = adresse;
        this.nombreParts = nombreParts;
        this.montantApport = montantApport;
        this.pourcentageDetention = pourcentageDetention;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Dossier getDossier() { return dossier; }
    public void setDossier(Dossier dossier) { this.dossier = dossier; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getCinOuPasseport() { return cinOuPasseport; }
    public void setCinOuPasseport(String cinOuPasseport) { this.cinOuPasseport = cinOuPasseport; }

    public String getNationalite() { return nationalite; }
    public void setNationalite(String nationalite) { this.nationalite = nationalite; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public Long getNombreParts() { return nombreParts; }
    public void setNombreParts(Long nombreParts) { this.nombreParts = nombreParts; }

    public BigDecimal getMontantApport() { return montantApport; }
    public void setMontantApport(BigDecimal montantApport) { this.montantApport = montantApport; }

    public BigDecimal getPourcentageDetention() { return pourcentageDetention; }
    public void setPourcentageDetention(BigDecimal pourcentageDetention) { this.pourcentageDetention = pourcentageDetention; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
}

