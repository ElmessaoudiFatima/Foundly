package com.backend.service.impl;

import com.backend.entity.Dossier;
import com.backend.entity.Paiement;
import com.backend.service.PdfGenerationService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class PdfGenerationServiceImpl implements PdfGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerationServiceImpl.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Africa/Casablanca"));

    @Override
    public byte[] generateStatutsPdf(Dossier dossier) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                float y = 790;
                float margin = 50;

                // En-tête
                drawText(cs, fontBold, 16, margin, y, "ROYAUME DU MAROC");
                y -= 20;
                drawText(cs, fontBold, 14, margin, y, "STATUTS CONSTITUTIFS TYPE");
                y -= 18;
                drawText(cs, fontRegular, 10, margin, y, "Modele de demonstration demonstratif genere par Foundly");
                y -= 25;

                // Titre Entreprise
                String nom = dossier.getNomEntreprise() != null ? dossier.getNomEntreprise() : "SOCIETE EN FORMATION";
                String forme = dossier.getFormeJuridique() != null ? dossier.getFormeJuridique().name() : "SARL";
                drawText(cs, fontBold, 14, margin, y, sanitize(nom + " (" + forme + ")"));
                y -= 25;

                // Article 1 : Forme
                drawText(cs, fontBold, 11, margin, y, "ARTICLE 1 - FORME JURIDIQUE");
                y -= 15;
                drawText(cs, fontRegular, 10, margin, y, "Il est forme entre les soussignes une societe a responsabilite limitee");
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "regie par la loi n 5-96 telle que modifiee et completee par les lois subséquentes.");
                y -= 25;

                // Article 2 : Dénomination
                drawText(cs, fontBold, 11, margin, y, "ARTICLE 2 - DENOMINATION SOCIALE");
                y -= 15;
                drawText(cs, fontRegular, 10, margin, y, "La denomination de la societe est : " + sanitize(nom));
                y -= 25;

                // Article 3 : Objet social
                drawText(cs, fontBold, 11, margin, y, "ARTICLE 3 - OBJET SOCIAL");
                y -= 15;
                String activite = dossier.getDescriptionActivite() != null && !dossier.getDescriptionActivite().isBlank()
                        ? dossier.getDescriptionActivite()
                        : "Activites commerciales, de prestations de services et d'ingenierie.";
                drawText(cs, fontRegular, 10, margin, y, sanitize(activite));
                y -= 25;

                // Article 4 : Siège social
                drawText(cs, fontBold, 11, margin, y, "ARTICLE 4 - SIEGE SOCIAL ET REGION");
                y -= 15;
                String siege = dossier.getAdresseSiege() != null ? dossier.getAdresseSiege() : "En cours de domiciliation";
                drawText(cs, fontRegular, 10, margin, y, "Siege social : " + sanitize(siege));
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "Ressort regional CRI : " + sanitize(dossier.getRegion()));
                y -= 25;

                // Article 5 : Capital social
                drawText(cs, fontBold, 11, margin, y, "ARTICLE 5 - CAPITAL SOCIAL ET PARTS");
                y -= 15;
                BigDecimal capital = dossier.getCapitalMad() != null ? dossier.getCapitalMad() : BigDecimal.ZERO;
                drawText(cs, fontRegular, 10, margin, y, "Le capital social s'eleve a : " + capital + " DIRHAMS (MAD).");
                y -= 14;
                if (dossier.isCapitalBlockageRequired()) {
                    drawText(cs, fontRegular, 10, margin, y, "Mention : Capital depassant 100 000 MAD. Attestation bancaire requise.");
                } else {
                    drawText(cs, fontRegular, 10, margin, y, "Mention : Dispense legale d'attestation de blocage bancaire (capital <= 100 000 MAD).");
                }
                y -= 25;

                // Article 6 : Fondateur
                drawText(cs, fontBold, 11, margin, y, "ARTICLE 6 - ASSOCIE / FONDATEUR PRINCIPAL");
                y -= 15;
                String fondateur = dossier.getEntrepreneur().getPrenom() + " " + dossier.getEntrepreneur().getNom();
                String cin = dossier.getEntrepreneur().getCin() != null ? dossier.getEntrepreneur().getCin() : "Non renseigne";
                drawText(cs, fontRegular, 10, margin, y, "Soussigne(e) : " + sanitize(fondateur) + " (CIN : " + sanitize(cin) + ")");
                y -= 35;

                // Bloc signature
                drawText(cs, fontBold, 10, margin + 280, y, "Fait a " + sanitize(dossier.getRegion()) + ",");
                y -= 15;
                drawText(cs, fontBold, 10, margin + 280, y, "Signature precedee de la mention :");
                y -= 15;
                drawText(cs, fontRegular, 9, margin + 280, y, "'Lu et approuve, bon pour acceptation des fonctions'");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Erreur lors de la generation du PDF des statuts : {}", e.getMessage(), e);
            throw new RuntimeException("Echec de la generation du PDF des statuts.", e);
        }
    }

    @Override
    public byte[] generateFormulaireUniqueCriPdf(Dossier dossier) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                float y = 790;
                float margin = 50;

                drawText(cs, fontBold, 15, margin, y, "CENTRE REGIONAL D'INVESTISSEMENT (CRI)");
                y -= 18;
                drawText(cs, fontBold, 13, margin, y, "FORMULAIRE UNIQUE DE CREATION D'ENTREPRISE");
                y -= 16;
                drawText(cs, fontRegular, 9, margin, y, "Dossier de reference : " + dossier.getReference());
                y -= 25;

                drawText(cs, fontBold, 11, margin, y, "1. RENSEIGNEMENTS SUR LE DECLARANT");
                y -= 15;
                String declarant = dossier.getEntrepreneur().getPrenom() + " " + dossier.getEntrepreneur().getNom();
                drawText(cs, fontRegular, 10, margin, y, "Nom & Prenom : " + sanitize(declarant));
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "CIN : " + sanitize(dossier.getEntrepreneur().getCin() != null ? dossier.getEntrepreneur().getCin() : "N/A"));
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "Email : " + sanitize(dossier.getEntrepreneur().getEmail()));
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "Telephone : " + sanitize(dossier.getEntrepreneur().getTelephone() != null ? dossier.getEntrepreneur().getTelephone() : "N/A"));
                y -= 25;

                drawText(cs, fontBold, 11, margin, y, "2. IDENTIFICATION DE L'ENTREPRISE");
                y -= 15;
                drawText(cs, fontRegular, 10, margin, y, "Denomination prevue : " + sanitize(dossier.getNomEntreprise() != null ? dossier.getNomEntreprise() : "N/A"));
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "Forme juridique : " + dossier.getFormeJuridique().name());
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "Region d'immatriculation : " + sanitize(dossier.getRegion()));
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "Capital social (MAD) : " + dossier.getCapitalMad());
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "Adresse du siege : " + sanitize(dossier.getAdresseSiege() != null ? dossier.getAdresseSiege() : "N/A"));
                y -= 25;

                drawText(cs, fontBold, 11, margin, y, "3. ADMINISTRATIONS PARTENAIRES CONCERNEES");
                y -= 15;
                drawText(cs, fontRegular, 10, margin, y, "- OMPIC : Certificat negatif et protection de nom commercial");
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "- Tribunal de Commerce : Immatriculation au Registre de Commerce (RC)");
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "- DGI : Attribution de l'Identifiant Fiscal (IF) et de la Patente");
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "- CNSS : Immatriculation de la societe et affiliation employeur");
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "- Imprimerie Officielle / Journal : Parution de l'avis de constitution");
                y -= 35;

                drawText(cs, fontBold, 10, margin, y, "Declaration sur l'honneur :");
                y -= 14;
                drawText(cs, fontRegular, 9, margin, y, "Je certifie sur l'honneur l'exactitude des informations mentionnees dans ce formulaire.");
                y -= 30;
                drawText(cs, fontBold, 10, margin + 300, y, "Cachet et Signature du Declarant");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Erreur lors de la generation du PDF formulaire unique CRI : {}", e.getMessage(), e);
            throw new RuntimeException("Echec de la generation du formulaire unique CRI.", e);
        }
    }

    @Override
    public byte[] generatePaymentReceiptPdf(Paiement paiement) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                float y = 790;
                float margin = 50;

                drawText(cs, fontBold, 16, margin, y, "FOUNDLY — RECU DE REGLEMENT SIMULE");
                y -= 20;
                drawText(cs, fontRegular, 10, margin, y, "Plateforme de Dematerialisation Administrative — Royaume du Maroc");
                y -= 25;

                drawText(cs, fontBold, 11, margin, y, "DETAILS DE LA TRANSACTION :");
                y -= 16;
                drawText(cs, fontRegular, 10, margin, y, "Reference de simulation : " + paiement.getReferenceSimulation());
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "Dossier associe : " + (paiement.getDossier() != null ? paiement.getDossier().getReference() : "N/A"));
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "Montant regle : " + paiement.getMontantMad() + " DIRHAMS (MAD TTC)");
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "Statut : " + paiement.getStatut().name());
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "Moyen utilise : " + paiement.getMethode());
                y -= 14;
                String date = paiement.getConfirmedAt() != null ? DATE_FMT.format(paiement.getConfirmedAt()) : "En attente";
                drawText(cs, fontRegular, 10, margin, y, "Date et heure de confirmation : " + date);
                y -= 25;

                drawText(cs, fontBold, 11, margin, y, "DESTINATION DES FRAIS :");
                y -= 15;
                String etapeLibelle = paiement.getSuiviEtape() != null ? paiement.getSuiviEtape().getLibelle() : "Frais administratifs OMPIC";
                drawText(cs, fontRegular, 10, margin, y, "Formalite : " + sanitize(etapeLibelle));
                y -= 14;
                drawText(cs, fontRegular, 10, margin, y, "TVA applicable : 20% incluse (Frais de greffe et d'enregistrement)");
                y -= 35;

                drawText(cs, fontRegular, 9, margin, y, "Document electronique simulatif a valeur pedagogique, conforme au cadre du projet Foundly.");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Erreur lors de la generation du recu de paiement : {}", e.getMessage(), e);
            throw new RuntimeException("Echec de la generation du recu de paiement.", e);
        }
    }

    private void drawText(PDPageContentStream cs, PDType1Font font, float fontSize, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private String sanitize(String str) {
        if (str == null) return "";
        // Normaliser les caractères accentués pour éviter les plantages avec Standard 14 Helvetica
        String n = Normalizer.normalize(str, Normalizer.Form.NFD);
        n = n.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        return n.replaceAll("[^\\x20-\\x7E]", " ");
    }
}
