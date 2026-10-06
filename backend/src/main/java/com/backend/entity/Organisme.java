package com.backend.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Entité représentant un organisme partenaire du circuit de création d'entreprise.
 * Le code est unique, non modifiable après création (il sert de clé métier / "sigle").
 */
@Entity
@Table(name = "organisme")
public class Organisme {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Sigle unique de l'organisme (ex. : OMPIC, CRI, DGI…).
     * Non modifiable après création.
     */
    @Column(unique = true, nullable = false, length = 20, updatable = false)
    private String code;

    /** Nom complet de l'organisme. */
    @Column(nullable = false, length = 150)
    private String nom;

    /** Description du rôle de l'organisme dans le circuit de création d'entreprise. */
    @Column(length = 500)
    private String description;

    public Organisme() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
