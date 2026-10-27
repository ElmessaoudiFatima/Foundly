package com.backend.service.impl;

import com.backend.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.from:notifications@foundly.ma}")
    private String mailFrom;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    public EmailServiceImpl(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendAgentWelcomeEmail(String toEmail, String agentFullName, String generatedPassword, String organization) {
        String loginUrl = frontendUrl.replaceAll("/+$", "") + "/login";
        String subject = "Bienvenue sur Foundly — Vos identifiants d'accès Agent (" + organization + ")";

        String htmlContent = """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
              <meta charset="UTF-8">
              <style>
                body { margin: 0; padding: 0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f6f9; color: #1e293b; }
                .email-container { max-width: 600px; margin: 30px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 16px rgba(0,0,0,0.06); border: 1px solid #e2e8f0; }
                .email-header { background: linear-gradient(135deg, #1F3864 0%%, #2d508f 100%%); padding: 32px 30px; text-align: center; color: #ffffff; }
                .email-header h1 { margin: 0 0 6px 0; font-size: 24px; font-weight: 700; letter-spacing: -0.5px; }
                .email-header p { margin: 0; font-size: 14px; opacity: 0.9; }
                .badge-tag { display: inline-block; background: rgba(255,255,255,0.15); padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.8px; margin-bottom: 12px; }
                .email-body { padding: 32px 30px; }
                .greeting { font-size: 17px; font-weight: 600; margin-bottom: 14px; color: #0f172a; }
                .info-text { font-size: 14px; line-height: 1.6; color: #475569; margin-bottom: 24px; }
                .credentials-card { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; margin-bottom: 26px; }
                .cred-row { display: flex; justify-content: space-between; margin-bottom: 12px; font-size: 14px; }
                .cred-row:last-child { margin-bottom: 0; }
                .cred-label { color: #64748b; font-weight: 500; }
                .cred-value { font-family: 'Consolas', 'Courier New', monospace; font-weight: 700; color: #0f172a; word-break: break-all; }
                .cred-password { background-color: #e0e7ff; color: #1e3a8a; padding: 4px 8px; border-radius: 4px; letter-spacing: 0.5px; font-size: 14px; }
                .action-wrap { text-align: center; margin: 30px 0; }
                .btn-primary { display: inline-block; background: linear-gradient(135deg, #1F3864 0%%, #2563EB 100%%); color: #ffffff !important; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: 600; font-size: 15px; box-shadow: 0 4px 12px rgba(37,99,235,0.25); }
                .notice-box { background: #fffbeb; border-left: 4px solid #f59e0b; padding: 12px 16px; border-radius: 0 6px 6px 0; margin-bottom: 24px; }
                .notice-box p { margin: 0; font-size: 13px; color: #92400e; line-height: 1.5; }
                .email-footer { background: #f8fafc; border-top: 1px solid #e2e8f0; padding: 20px 30px; text-align: center; font-size: 12px; color: #94a3b8; }
              </style>
            </head>
            <body>
              <div class="email-container">
                <div class="email-header">
                  <span class="badge-tag">Plateforme Nationale Foundly</span>
                  <h1>Bienvenue sur Foundly</h1>
                  <p>Votre compte agent administratif est désormais opérationnel</p>
                </div>
                <div class="email-body">
                  <div class="greeting">Bonjour %s,</div>
                  <p class="info-text">
                    L'administrateur de la plateforme <strong>Foundly</strong> vient de vous créer un compte agent rattaché à l'organisme partenaire <strong>%s</strong>.
                    Vous pouvez dès à présent vous connecter pour traiter et instruire les dossiers de création d'entreprise qui vous sont affectés.
                  </p>
                  
                  <div class="credentials-card">
                    <table width="100%%" cellpadding="6" cellspacing="0" style="font-size: 14px;">
                      <tr>
                        <td style="color: #64748b; width: 40%%;">Identifiant (E-mail) :</td>
                        <td style="font-weight: 700; color: #0f172a; font-family: monospace;">%s</td>
                      </tr>
                      <tr>
                        <td style="color: #64748b;">Mot de passe temporaire :</td>
                        <td><span class="cred-password">%s</span></td>
                      </tr>
                      <tr>
                        <td style="color: #64748b;">Organisme :</td>
                        <td style="font-weight: 600; color: #0f172a;">%s</td>
                      </tr>
                    </table>
                  </div>

                  <div class="notice-box">
                    <p>
                      <strong>Recommandation de sécurité :</strong> Ce mot de passe a été généré automatiquement par le système.
                      Après votre première connexion, nous vous conseillons de le modifier pour sécuriser votre espace de travail.
                    </p>
                  </div>

                  <div class="action-wrap">
                    <a href="%s" class="btn-primary" target="_blank">Se connecter à mon espace agent &rarr;</a>
                  </div>

                  <p class="info-text" style="font-size: 12px; color: #94a3b8; text-align: center; margin-bottom: 0;">
                    Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :<br/>
                    <a href="%s" style="color: #2563EB;">%s</a>
                  </p>
                </div>
                <div class="email-footer">
                  Foundly &bull; Digitalisation de la création d'entreprise au Maroc &bull; Ce message automatique est confidentiel.
                </div>
              </div>
            </body>
            </html>
            """.formatted(
                escapeHtml(agentFullName),
                escapeHtml(organization),
                escapeHtml(toEmail),
                escapeHtml(generatedPassword),
                escapeHtml(organization),
                loginUrl,
                loginUrl,
                loginUrl
        );

        sendHtmlEmail(toEmail, subject, htmlContent, "WELCOME [agent=" + toEmail + ", tempPassword=" + generatedPassword + "]");
    }

    @Override
    public void sendAgentDeactivationEmail(String toEmail, String agentFullName, String reason) {
        String subject = "Foundly — Notification de désactivation de votre compte agent";

        String htmlContent = """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
              <meta charset="UTF-8">
              <style>
                body { margin: 0; padding: 0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f6f9; color: #1e293b; }
                .email-container { max-width: 600px; margin: 30px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 16px rgba(0,0,0,0.06); border: 1px solid #e2e8f0; }
                .email-header { background: linear-gradient(135deg, #7f1d1d 0%%, #b91c1c 100%%); padding: 32px 30px; text-align: center; color: #ffffff; }
                .email-header h1 { margin: 0 0 6px 0; font-size: 22px; font-weight: 700; letter-spacing: -0.5px; }
                .email-header p { margin: 0; font-size: 14px; opacity: 0.9; }
                .badge-tag { display: inline-block; background: rgba(255,255,255,0.15); padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.8px; margin-bottom: 12px; }
                .email-body { padding: 32px 30px; }
                .greeting { font-size: 17px; font-weight: 600; margin-bottom: 14px; color: #0f172a; }
                .info-text { font-size: 14px; line-height: 1.6; color: #475569; margin-bottom: 20px; }
                .reason-card { background: #fef2f2; border: 1px solid #fecaca; border-left: 5px solid #dc2626; border-radius: 6px; padding: 18px 20px; margin: 20px 0 24px 0; }
                .reason-title { font-size: 13px; font-weight: 700; text-transform: uppercase; color: #991b1b; letter-spacing: 0.5px; margin-bottom: 6px; }
                .reason-text { font-size: 14px; color: #7f1d1d; line-height: 1.5; font-style: italic; }
                .warning-box { background: #f8fafc; border: 1px solid #e2e8f0; padding: 16px 20px; border-radius: 8px; margin-bottom: 24px; }
                .warning-box ul { margin: 6px 0 0 0; padding-left: 20px; font-size: 13px; color: #475569; }
                .email-footer { background: #f8fafc; border-top: 1px solid #e2e8f0; padding: 20px 30px; text-align: center; font-size: 12px; color: #94a3b8; }
              </style>
            </head>
            <body>
              <div class="email-container">
                <div class="email-header">
                  <span class="badge-tag">Administration centrale Foundly</span>
                  <h1>Notification de désactivation de compte</h1>
                  <p>Votre accès agent a été suspendu par l'administrateur</p>
                </div>
                <div class="email-body">
                  <div class="greeting">Bonjour %s,</div>
                  <p class="info-text">
                    Nous vous informons que votre compte agent sur la plateforme nationale <strong>Foundly</strong> a été désactivé par l'administration centrale à compter de ce jour.
                  </p>

                  <div class="reason-card">
                    <div class="reason-title">Motif officiel de désactivation :</div>
                    <div class="reason-text">« %s »</div>
                  </div>

                  <div class="warning-box">
                    <strong style="color: #0f172a; font-size: 13px;">Conséquences immédiates :</strong>
                    <ul>
                      <li>Il n'est désormais plus possible de vous connecter à votre espace agent.</li>
                      <li>Toutes vos sessions en cours ont été invalidées.</li>
                      <li>Vos accès au traitement et à l'instruction des dossiers sont suspendus.</li>
                    </ul>
                  </div>

                  <p class="info-text" style="font-size: 13px; margin-bottom: 0;">
                    Si vous estimez qu'il s'agit d'une erreur ou si vous avez des questions relatives à cette décision, veuillez contacter votre responsable d'organisme ou l'administrateur de la plateforme Foundly.
                  </p>
                </div>
                <div class="email-footer">
                  Foundly &bull; Digitalisation de la création d'entreprise au Maroc &bull; Notification automatique
                </div>
              </div>
            </body>
            </html>
            """.formatted(
                escapeHtml(agentFullName),
                escapeHtml(reason)
        );

        sendHtmlEmail(toEmail, subject, htmlContent, "DEACTIVATION [agent=" + toEmail + ", reason=" + reason + "]");
    }

    private void sendHtmlEmail(String toEmail, String subject, String htmlContent, String contextLog) {
        boolean smtpConfigured = mailSender != null && mailUsername != null && !mailUsername.trim().isEmpty();

        if (smtpConfigured) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

                // Alignement SPF/DKIM : si mailFrom est le domaine factice foundly.ma ou vide,
                // utiliser le compte authentifié (mailUsername) avec le libellé « Foundly »
                String fromAddress = (mailFrom != null && !mailFrom.isBlank() && !mailFrom.contains("@foundly.ma"))
                        ? mailFrom
                        : mailUsername;
                helper.setFrom(fromAddress, "Foundly");
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(htmlContent, true);

                mailSender.send(message);
                log.info("E-mail envoyé avec succès à {} (From: {}) : {}", toEmail, fromAddress, contextLog);
                return;
            } catch (Exception e) {
                log.error("Échec d'envoi de l'e-mail via SMTP ({}) à {} : {}",
                        contextLog, toEmail, e.getMessage(), e);
            }
        } else {
            log.info("Serveur SMTP non configuré (spring.mail.username vide). Simulation de l'envoi d'e-mail.");
        }

        // Journal de secours lisible (pour développement et vérification sans SMTP externe)
        log.info("""
                ================================================================================
                [FOUNDLY NOTIFICATION MAIL - %s]
                Destinataire : %s
                Sujet        : %s
                Contexte     : %s
                ================================================================================
                """.formatted(smtpConfigured ? "SIMULATION APRES ERREUR SMTP" : "DEV/SIMULATION", toEmail, subject, contextLog));
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
