package com.backend.service;

public interface EmailService {

    /**
     * Envoie un e-mail de bienvenue à un nouvel agent avec ses identifiants temporaires
     * et un lien direct pour se connecter à son espace.
     *
     * @param toEmail           Adresse e-mail de l'agent (identifiant)
     * @param agentFullName     Nom complet de l'agent
     * @param generatedPassword Mot de passe généré automatiquement
     * @param organization      Organisme rattaché
     */
    void sendAgentWelcomeEmail(String toEmail, String agentFullName, String generatedPassword, String organization);

    /**
     * Envoie un e-mail de notification de désactivation à un agent avec le motif officiel.
     *
     * @param toEmail       Adresse e-mail de l'agent
     * @param agentFullName Nom complet de l'agent
     * @param reason        Motif / justification de la désactivation
     */
    void sendAgentDeactivationEmail(String toEmail, String agentFullName, String reason);

    /**
     * Envoie un e-mail de notification de suivi de dossier à un entrepreneur.
     */
    void sendDossierNotificationEmail(String toEmail, String recipientName, String title, String message, String dossierReference, String actionUrl);
}

