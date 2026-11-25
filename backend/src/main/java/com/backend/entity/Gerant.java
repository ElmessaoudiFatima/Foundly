package com.backend.entity;

import com.backend.enums.ModeNominationGerant;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "gerant")
public class Gerant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "associe_id")
    private Associe associe;

    @Column(nullable = false)
    private boolean estAssocie;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ModeNominationGerant modeNomination = ModeNominationGerant.STATUTAIRE;

    @Column(length = 50)
    private String dureeMandat = "Indéterminée";

    @Column(length = 500)
    private String pouvoirs;

    @Column(nullable = false)
    private boolean actif = true;

    public Gerant() {}

    public Gerant(Dossier dossier, Associe associe, boolean estAssocie, String nom, String prenom,
                  String cinOuPasseport, String nationalite, String adresse,
                  ModeNominationGerant modeNomination, String dureeMandat, String pouvoirs) {
        this.dossier = dossier;
        this.associe = associe;
        this.estAssocie = estAssocie;
        this.nom = nom;
        this.prenom = prenom;
        this.cinOuPasseport = cinOuPasseport;
        this.nationalite = nationalite;
        this.adresse = adresse;
        this.modeNomination = modeNomination != null ? modeNomination : ModeNominationGerant.STATUTAIRE;
        this.dureeMandat = dureeMandat != null ? dureeMandat : "Indéterminée";
        this.pouvoirs = pouvoirs;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Dossier getDossier() { return dossier; }
    public void setDossier(Dossier dossier) { this.dossier = dossier; }

    public Associe getAssocie() { return associe; }
    public void setAssocie(Associe associe) { this.associe = associe; }

    public boolean isEstAssocie() { return estAssocie; }
    public void setEstAssocie(boolean estAssocie) { this.estAssocie = estAssocie; }

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

    public ModeNominationGerant getModeNomination() { return modeNomination; }
    public void setModeNomination(ModeNominationGerant modeNomination) { this.modeNomination = modeNomination; }

    public String getDureeMandat() { return dureeMandat; }
    public void setDureeMandat(String dureeMandat) { this.dureeMandat = dureeMandat; }

    public String getPouvoirs() { return pouvoirs; }
    public void setPouvoirs(String pouvoirs) { this.pouvoirs = pouvoirs; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
}

