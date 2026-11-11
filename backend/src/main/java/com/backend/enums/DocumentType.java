package com.backend.enums;

public enum DocumentType {
    CIN("CIN / Titre de séjour des fondateurs ou du gérant"),
    JUSTIFICATIF_SIEGE("Justificatif du siège social (Contrat de bail ou domiciliation)"),
    STATUTS_SIGNES("Statuts signés de la société"),
    ATTESTATION_BLOCAGE_CAPITAL("Attestation bancaire de blocage du capital social"),
    CERTIFICAT_NEGATIF("Certificat négatif délivré par l'OMPIC"),
    FORMULAIRE_UNIQUE("Formulaire unique de création d'entreprise (CRI)"),
    PV_NOMINATION_GERANT("Procès-verbal de nomination de la gérance"),
    ATTESTATION_PARUTION("Attestation de parution de l'annonce légale"),
    AUTRE("Pièce justificative complémentaire");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
