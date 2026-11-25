package com.backend.service.impl;

import com.backend.entity.Associe;
import com.backend.entity.Dossier;
import com.backend.entity.Gerant;
import com.backend.entity.Paiement;
import com.backend.enums.LegalFormCode;
import com.backend.enums.ModeNominationGerant;
import com.backend.service.PdfGenerationService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfGenerationServiceImpl implements PdfGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerationServiceImpl.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Africa/Casablanca"));

    // Chemins des polices TrueType (classpath) — DejaVu Sans (SIL Open Font License — redistribution libre)
    private static final String FONT_REGULAR_PATH = "/fonts/DejaVuSans.ttf";
    private static final String FONT_BOLD_PATH    = "/fonts/DejaVuSans-Bold.ttf";

    // =========================================================================
    // Constantes de mise en page
    // =========================================================================
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();   // 595 pt
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight(); // 842 pt
    private static final float MARGIN = 50f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;  // 495 pt
    private static final float TOP_Y = 800f;
    private static final float BOTTOM_Y = 55f;  // espace pour numéro de page

    // =========================================================================
    // Chargement des polices TrueType Unicode
    // =========================================================================

    /**
     * Charge une police TrueType depuis le classpath.
     * En cas d'échec (ressource absente), retourne null — l'appelant bascule sur Helvetica Standard-14.
     */
    private PDFont loadTtfFont(PDDocument doc, String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                log.warn("Police TTF introuvable en classpath : {} — bascule sur Helvetica Standard-14", path);
                return null;
            }
            return PDType0Font.load(doc, is, true);
        } catch (IOException e) {
            log.warn("Impossible de charger la police TTF {} : {} — bascule sur Helvetica Standard-14", path, e.getMessage());
            return null;
        }
    }

    /**
     * Résout la police à utiliser : police TTF Unicode si disponible, sinon Helvetica Standard-14 (fallback).
     * <p>Attention : avec Helvetica, les accents sont perdus (sanitize() s'en charge). Avec TTF, les
     * accents sont conservés — il ne faut PAS appeler sanitize() sur ces textes.</p>
     */
    private PDFont resolveFont(PDFont ttf, PDType1Font fallback) {
        return (ttf != null) ? ttf : fallback;
    }

    // =========================================================================
    // Contexte de rendu multi-pages (avec polices TTF)
    // =========================================================================
    private static class RenderContext {
        final PDDocument doc;
        PDPage currentPage;
        PDPageContentStream cs;
        float y;
        int pageNumber = 1;
        final PDFont fontBold;
        final PDFont fontRegular;
        final PDFont fontBoldSmall;
        // Police Helvetica de secours pour les textes ne nécessitant pas d'Unicode
        final PDType1Font helv;
        final PDType1Font helvBold;

        RenderContext(PDDocument doc, PDFont ttfRegular, PDFont ttfBold) throws IOException {
            this.doc = doc;
            this.helv = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            this.helvBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            // Utiliser TTF si disponible, sinon Helvetica (accents perdus)
            this.fontRegular = (ttfRegular != null) ? ttfRegular : helv;
            this.fontBold    = (ttfBold    != null) ? ttfBold    : helvBold;
            this.fontBoldSmall = this.fontBold;
            newPage();
        }

        void newPage() throws IOException {
            if (cs != null) {
                // Numéro de page en bas
                cs.beginText();
                cs.setFont(helv, 8);
                cs.newLineAtOffset(PAGE_WIDTH / 2 - 20, 30);
                cs.showText("Page " + pageNumber);
                cs.endText();
                cs.close();
            }
            pageNumber++;
            currentPage = new PDPage(PDRectangle.A4);
            doc.addPage(currentPage);
            cs = new PDPageContentStream(doc, currentPage);
            y = TOP_Y;
        }

        /** Vérifie si on a assez d'espace, sinon tourne la page. */
        void ensureSpace(float needed) throws IOException {
            if (y - needed < BOTTOM_Y) {
                newPage();
            }
        }

        void close() throws IOException {
            if (cs != null) {
                // Dernière page : numéro de page
                cs.beginText();
                cs.setFont(helv, 8);
                cs.newLineAtOffset(PAGE_WIDTH / 2 - 20, 30);
                cs.showText("Page " + (pageNumber - 1));
                cs.endText();
                cs.close();
                cs = null;
            }
        }
    }

    // =========================================================================
    // Méthodes d'aide au rendu
    // =========================================================================

    /**
     * Rendu d'un texte brut à une position donnée.
     * Si la police est TTF (PDType0Font), on passe le texte tel quel (accents conservés).
     * Si la police est Standard-14, on sanitize pour éviter les crashs.
     */
    private void renderText(RenderContext ctx, PDFont font, float size, float x, String text) throws IOException {
        ctx.cs.beginText();
        ctx.cs.setFont(font, size);
        ctx.cs.newLineAtOffset(x, ctx.y);
        ctx.cs.showText(prepareText(font, text));
        ctx.cs.endText();
    }

    private void renderLine(RenderContext ctx, PDFont font, float size, float x, String text, float leading) throws IOException {
        ctx.ensureSpace(leading + 4);
        renderText(ctx, font, size, x, text);
        ctx.y -= leading;
    }

    private void renderSectionTitle(RenderContext ctx, String title) throws IOException {
        ctx.ensureSpace(30);
        renderText(ctx, ctx.fontBold, 11, MARGIN, title);
        ctx.y -= 16;
    }

    private void renderBody(RenderContext ctx, String text) throws IOException {
        // Découpe le texte long en lignes de ~90 caractères
        List<String> lines = wrapText(text, ctx.fontRegular, 90);
        for (String line : lines) {
            ctx.ensureSpace(15);
            renderText(ctx, ctx.fontRegular, 10, MARGIN, line);
            ctx.y -= 14;
        }
    }

    private void renderSpacer(RenderContext ctx, float space) throws IOException {
        ctx.ensureSpace(space);
        ctx.y -= space;
    }

    /**
     * Découpe un texte long en lignes de maxChars caractères maximum.
     * N'applique sanitize() que si la police est Standard-14.
     */
    private List<String> wrapText(String text, PDFont font, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        // Pour TTF : conserver les accents. Pour Standard-14 : sanitize.
        String prepared = prepareText(font, text);
        String[] words = prepared.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() + word.length() + 1 > maxChars) {
                if (current.length() > 0) lines.add(current.toString().trim());
                current = new StringBuilder(word).append(" ");
            } else {
                current.append(word).append(" ");
            }
        }
        if (current.length() > 0) lines.add(current.toString().trim());
        return lines;
    }

    /**
     * Prépare un texte pour l'affichage :
     * - Si la police est TTF (PDType0Font) : on renvoie le texte tel quel (accents préservés).
     * - Si la police est Standard-14 : on sanitize (accents supprimés pour éviter les crashs).
     */
    private String prepareText(PDFont font, String text) {
        if (text == null) return "";
        if (font instanceof PDType0Font) {
            // Police TTF : accents conservés, on filtre seulement les caractères de contrôle
            return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        }
        // Police Standard-14 : sanitize NFD→ASCII
        return sanitize(text);
    }

    /**
     * Dessine un tableau simple avec des colonnes de largeurs fixes.
     * @param headers titres de colonnes
     * @param rows    lignes de données (autant de cellules que de headers)
     * @param colWidths largeurs de colonnes en points
     */
    private void renderTable(RenderContext ctx, String[] headers, List<String[]> rows, float[] colWidths) throws IOException {
        float rowHeight = 18f;
        float tableWidth = 0;
        for (float w : colWidths) tableWidth += w;

        // Vérifier qu'on a de la place pour l'en-tête + au moins 2 lignes
        ctx.ensureSpace(rowHeight * 3);

        // Ligne d'en-tête
        drawTableRow(ctx, headers, colWidths, rowHeight, true);

        // Lignes de données
        for (String[] row : rows) {
            ctx.ensureSpace(rowHeight + 2);
            drawTableRow(ctx, row, colWidths, rowHeight, false);
        }
        ctx.y -= 4; // espace après tableau
    }

    private void drawTableRow(RenderContext ctx, String[] cells, float[] colWidths, float rowHeight, boolean isHeader) throws IOException {
        PDFont font = isHeader ? ctx.fontBold : ctx.fontRegular;
        float cellHeight = rowHeight;
        float startY = ctx.y;
        float startX = MARGIN;

        // Rectangle de la ligne entière
        ctx.cs.setNonStrokingColor(isHeader ? 0.85f : 1.0f);
        ctx.cs.addRect(startX, startY - cellHeight + 4, CONTENT_WIDTH, cellHeight);
        ctx.cs.fill();
        ctx.cs.setNonStrokingColor(0f); // reset à noir

        // Bordure horizontale
        ctx.cs.setStrokingColor(0.5f);
        ctx.cs.setLineWidth(0.5f);
        ctx.cs.moveTo(startX, startY - cellHeight + 4);
        ctx.cs.lineTo(startX + CONTENT_WIDTH, startY - cellHeight + 4);
        ctx.cs.stroke();
        ctx.cs.setStrokingColor(0f);

        // Texte des cellules
        float x = startX + 3;
        for (int i = 0; i < cells.length && i < colWidths.length; i++) {
            String raw = cells[i] != null ? cells[i] : "";
            String cell = prepareText(font, raw);
            // Tronquer si trop long (après préparation)
            if (cell.length() > 30) cell = cell.substring(0, 27) + "...";
            ctx.cs.beginText();
            ctx.cs.setFont(font, 9);
            ctx.cs.newLineAtOffset(x, startY - 12);
            ctx.cs.showText(cell);
            ctx.cs.endText();
            x += colWidths[i];
        }
        ctx.y -= cellHeight;
    }

    private void renderHorizontalRule(RenderContext ctx) throws IOException {
        ctx.cs.setStrokingColor(0.3f);
        ctx.cs.setLineWidth(0.8f);
        ctx.cs.moveTo(MARGIN, ctx.y);
        ctx.cs.lineTo(MARGIN + CONTENT_WIDTH, ctx.y);
        ctx.cs.stroke();
        ctx.cs.setStrokingColor(0f);
        ctx.y -= 8;
    }

    private void renderPageHeader(RenderContext ctx, String companyName, String docTitle) throws IOException {
        ctx.cs.setNonStrokingColor(0.95f);
        ctx.cs.addRect(MARGIN, TOP_Y - 5, CONTENT_WIDTH, 24);
        ctx.cs.fill();
        ctx.cs.setNonStrokingColor(0f);
        ctx.cs.beginText();
        ctx.cs.setFont(ctx.fontBold, 10);
        ctx.cs.newLineAtOffset(MARGIN + 4, TOP_Y + 14);
        ctx.cs.showText(prepareText(ctx.fontBold, docTitle + " — " + companyName));
        ctx.cs.endText();
        ctx.y = TOP_Y - 30;
    }

    // =========================================================================
    // generateStatutsPdf — Version SARL multi-associés / multi-gérants
    // =========================================================================

    @Override
    public byte[] generateStatutsPdf(Dossier dossier) {
        // Déléguer au générateur SARL dédié si la forme le justifie
        if (dossier.getFormeJuridique() == LegalFormCode.SARL
                || dossier.getFormeJuridique() == LegalFormCode.SARL_AU) {
            return generateStatutsSarlPdf(dossier);
        }
        // Fallback : autres formes (AE, etc.) — statuts simplifiés mono-page
        return generateStatutsSimplesPdf(dossier);
    }

    // =========================================================================
    // Validation des données obligatoires pour statuts SARL
    // =========================================================================

    /**
     * Détecte de façon exhaustive les données obligatoires manquantes pour la production
     * de statuts SARL / SARL-AU complets et juridiquement exploitables.
     *
     * <p>Vérifications individuelles :
     * <ul>
     *   <li>1. Dénomination sociale</li>
     *   <li>2. Siège social (adresseSiege)</li>
     *   <li>3. Objet social (descriptionActivite)</li>
     *   <li>4. Capital social &gt; 0 et valeur nominale &gt; 0, divisibilité exacte</li>
     *   <li>5. Nombre d'associés actifs (min 2 pour SARL, min 1 pour SARL-AU, max 50)</li>
     *   <li>6. Pour chaque associé : nom, prénom, nationalité, adresse, CIN/passeport, parts &gt; 0, apport &gt; 0</li>
     *   <li>7. Cohérence individuelle : apport de chaque associé = nombre de parts × valeur nominale</li>
     *   <li>8. Cohérence globale : somme des parts = nombre total de parts, somme des apports = capital social</li>
     *   <li>9. Au moins un gérant actif</li>
     *   <li>10. Pour chaque gérant : nom, prénom, nationalité, adresse, mode de nomination</li>
     *   <li>11. Pour chaque gérant statutaire : CIN/passeport et durée du mandat</li>
     * </ul>
     *
     * @return liste des données manquantes ou incohérences détectées (vide si dossier complet)
     */
    private List<String> detectDonneeManquantes(Dossier dossier) {
        List<String> manquantes = new ArrayList<>();

        // ── 1. Dénomination sociale ─────────────────────────────────────────
        if (dossier.getNomEntreprise() == null || dossier.getNomEntreprise().isBlank()) {
            manquantes.add("Dénomination sociale (nomEntreprise)");
        }

        // ── 2. Siège social ─────────────────────────────────────────────────
        if (dossier.getAdresseSiege() == null || dossier.getAdresseSiege().isBlank()) {
            manquantes.add("Adresse du siège social (adresseSiege)");
        }

        // ── 3. Objet social ─────────────────────────────────────────────────
        if (dossier.getDescriptionActivite() == null || dossier.getDescriptionActivite().isBlank()) {
            manquantes.add("Objet social / description de l'activité (descriptionActivite)");
        }

        // ── 4. Capital social & Valeur nominale & Divisibilité ──────────────
        BigDecimal capital = dossier.getCapitalMad();
        boolean capitalValide = true;
        if (capital == null || capital.compareTo(BigDecimal.ZERO) <= 0) {
            manquantes.add("Capital social (capitalMad doit être strictement supérieur à zéro)");
            capital = BigDecimal.ZERO;
            capitalValide = false;
        }

        BigDecimal valeurNominale = dossier.getValeurNominalePart();
        boolean vnValide = true;
        if (valeurNominale == null || valeurNominale.compareTo(BigDecimal.ZERO) <= 0) {
            manquantes.add("Valeur nominale de la part (valeurNominalePart doit être strictement supérieure à zéro)");
            valeurNominale = BigDecimal.ONE;
            vnValide = false;
        }

        if (capitalValide && vnValide) {
            if (capital.remainder(valeurNominale).compareTo(BigDecimal.ZERO) != 0) {
                manquantes.add("Divisibilité du capital : " + capital + " MAD n'est pas divisible exactement par " + valeurNominale + " MAD");
            }
        }

        // ── 5. Nombre d'associés actifs ─────────────────────────────────────
        List<Associe> associesActifs = getAssociesActifs(dossier);
        boolean isSarlAu = dossier.getFormeJuridique() == LegalFormCode.SARL_AU;
        int minAssocies = isSarlAu ? 1 : 2;
        if (associesActifs.size() < minAssocies) {
            manquantes.add("Associés actifs insuffisants : minimum " + minAssocies
                    + " requis pour " + dossier.getFormeJuridique()
                    + " (trouvés : " + associesActifs.size() + ")");
        } else if (associesActifs.size() > 50) {
            manquantes.add("Nombre d'associés dépasse le plafond légal de 50 pour une SARL (trouvés : " + associesActifs.size() + ")");
        }

        // ── 6 & 7. Identité complète, apports et parts de chaque associé ────
        BigDecimal totalApportsAssocies = BigDecimal.ZERO;
        long      totalPartsAssocies    = 0L;

        for (int i = 0; i < associesActifs.size(); i++) {
            Associe a = associesActifs.get(i);
            String ref = "Associé n°" + (i + 1)
                    + (a.getNom() != null && !a.getNom().isBlank() ? " (" + a.getNom() + ")" : "");

            if (a.getNom() == null || a.getNom().isBlank()) {
                manquantes.add(ref + " : nom manquant");
            }
            if (a.getPrenom() == null || a.getPrenom().isBlank()) {
                manquantes.add(ref + " : prénom manquant");
            }
            if (a.getCinOuPasseport() == null || a.getCinOuPasseport().isBlank()) {
                manquantes.add(ref + " : CIN ou passeport manquant");
            }
            if (a.getNationalite() == null || a.getNationalite().isBlank()) {
                manquantes.add(ref + " : nationalité manquante");
            }
            if (a.getAdresse() == null || a.getAdresse().isBlank()) {
                manquantes.add(ref + " : adresse de domicile manquante");
            }

            boolean partsValides = true;
            if (a.getNombreParts() == null || a.getNombreParts() <= 0) {
                manquantes.add(ref + " : nombre de parts invalide ou nul");
                partsValides = false;
            } else {
                totalPartsAssocies += a.getNombreParts();
            }

            boolean apportValide = true;
            if (a.getMontantApport() == null) {
                manquantes.add(ref + " : montant d'apport manquant");
                apportValide = false;
            } else if (a.getMontantApport().compareTo(BigDecimal.ZERO) <= 0) {
                manquantes.add(ref + " : montant d'apport doit être strictement positif");
                apportValide = false;
            } else {
                totalApportsAssocies = totalApportsAssocies.add(a.getMontantApport());
            }

            // Cohérence individuelle apport = parts × valeur nominale
            if (partsValides && apportValide && vnValide) {
                BigDecimal apportCalcule = valeurNominale.multiply(BigDecimal.valueOf(a.getNombreParts()));
                if (a.getMontantApport().compareTo(apportCalcule) != 0) {
                    manquantes.add(ref + " : montant apport (" + a.getMontantApport()
                            + " MAD) incohérent avec nombre de parts (" + a.getNombreParts()
                            + " × " + valeurNominale + " = " + apportCalcule + " MAD)");
                }
            }
        }

        // ── 8. Cohérence globale capital / parts / apports ─────────────────
        if (!associesActifs.isEmpty() && capitalValide) {
            // Somme des apports == capital déclaré
            if (totalApportsAssocies.compareTo(capital) != 0) {
                manquantes.add("Incohérence capital : somme des apports (" + totalApportsAssocies
                        + " MAD) ≠ capital déclaré (" + capital + " MAD)");
            }
            // Somme des parts == nombre total de parts du dossier
            if (dossier.getNombreTotalParts() != null
                    && dossier.getNombreTotalParts() > 0
                    && totalPartsAssocies != dossier.getNombreTotalParts()) {
                manquantes.add("Incohérence parts : somme des parts (" + totalPartsAssocies
                        + ") ≠ total parts du dossier (" + dossier.getNombreTotalParts() + ")");
            }
        }

        // ── 9. Au moins un gérant actif ─────────────────────────────────────
        List<Gerant> gerantsActifs = getGerantsActifs(dossier);
        if (gerantsActifs.isEmpty()) {
            manquantes.add("Au moins un gérant actif");
        }

        // ── 10 & 11. Identité complète, nomination, mandat des gérants ─────
        for (int i = 0; i < gerantsActifs.size(); i++) {
            Gerant g = gerantsActifs.get(i);
            String ref = "Gérant n°" + (i + 1)
                    + (g.getNom() != null && !g.getNom().isBlank() ? " (" + g.getNom() + ")" : "");

            if (g.getNom() == null || g.getNom().isBlank()) {
                manquantes.add(ref + " : nom manquant");
            }
            if (g.getPrenom() == null || g.getPrenom().isBlank()) {
                manquantes.add(ref + " : prénom manquant");
            }
            if (g.getNationalite() == null || g.getNationalite().isBlank()) {
                manquantes.add(ref + " : nationalité manquante");
            }
            if (g.getAdresse() == null || g.getAdresse().isBlank()) {
                manquantes.add(ref + " : adresse manquante");
            }
            if (g.getModeNomination() == null) {
                manquantes.add(ref + " : mode de nomination non défini");
            }

            if (g.getModeNomination() == ModeNominationGerant.STATUTAIRE) {
                if (g.getCinOuPasseport() == null || g.getCinOuPasseport().isBlank()) {
                    manquantes.add(ref + " (statutaire) : CIN ou passeport manquant");
                }
                if (g.getDureeMandat() == null || g.getDureeMandat().isBlank()) {
                    manquantes.add(ref + " (statutaire) : durée du mandat manquante");
                }
            }
        }

        return manquantes;
    }

    // -------------------------------------------------------------------------
    // Statuts SARL complets (multi-pages, multi-associés, multi-gérants)
    // -------------------------------------------------------------------------

    private byte[] generateStatutsSarlPdf(Dossier dossier) {
        try (PDDocument doc = new PDDocument()) {

            // Charger les polices TTF Unicode (Arial)
            PDFont ttfRegular = loadTtfFont(doc, FONT_REGULAR_PATH);
            PDFont ttfBold    = loadTtfFont(doc, FONT_BOLD_PATH);

            // Détecter les données manquantes
            List<String> donneeManquantes = detectDonneeManquantes(dossier);
            boolean brouillonIncomplet = !donneeManquantes.isEmpty();

            // --- Page de garde ---
            addCoverPage(doc, dossier, ttfRegular, ttfBold, brouillonIncomplet);

            // --- Corps du document ---
            RenderContext ctx = new RenderContext(doc, ttfRegular, ttfBold);
            String companyName = dossier.getNomEntreprise() != null ? dossier.getNomEntreprise() : "SOCIETE EN FORMATION";
            String formeLabel = dossier.getFormeJuridique() == LegalFormCode.SARL_AU
                    ? "Société à Responsabilité Limitée à Associé Unique (SARL-AU)"
                    : "Société à Responsabilité Limitée (SARL)";

            String docTitle = brouillonIncomplet ? "STATUTS CONSTITUTIFS [BROUILLON INCOMPLET]" : "STATUTS CONSTITUTIFS";
            renderPageHeader(ctx, companyName, docTitle);
            renderHorizontalRule(ctx);

            // === MENTION BROUILLON INCOMPLET ===
            if (brouillonIncomplet) {
                float boxHeight = 26f + donneeManquantes.size() * 15f;
                ctx.ensureSpace(boxHeight + 15);
                // Encadré rouge d'avertissement
                ctx.cs.setNonStrokingColor(1f, 0.93f, 0.93f);
                ctx.cs.addRect(MARGIN, ctx.y - boxHeight + 4, CONTENT_WIDTH, boxHeight);
                ctx.cs.fill();
                ctx.cs.setNonStrokingColor(0.85f, 0.1f, 0.1f);
                ctx.cs.setStrokingColor(0.85f, 0.1f, 0.1f);
                ctx.cs.setLineWidth(1.2f);
                ctx.cs.addRect(MARGIN, ctx.y - boxHeight + 4, CONTENT_WIDTH, boxHeight);
                ctx.cs.stroke();
                ctx.cs.setNonStrokingColor(0f);
                ctx.cs.setStrokingColor(0f);

                ctx.cs.beginText();
                ctx.cs.setFont(ctx.fontBold, 10);
                ctx.cs.newLineAtOffset(MARGIN + 8, ctx.y - 12);
                ctx.cs.showText(prepareText(ctx.fontBold, "BROUILLON INCOMPLET — Données obligatoires manquantes :"));
                ctx.cs.endText();
                ctx.y -= 26;

                for (String donnee : donneeManquantes) {
                    ctx.cs.beginText();
                    ctx.cs.setFont(ctx.fontRegular, 8.5f);
                    ctx.cs.newLineAtOffset(MARGIN + 14, ctx.y);
                    ctx.cs.showText(prepareText(ctx.fontRegular, "• " + donnee));
                    ctx.cs.endText();
                    ctx.y -= 14;
                }
                ctx.y -= 10;
            }

            // A1 — Forme juridique
            renderSectionTitle(ctx, "ARTICLE 1 - FORME JURIDIQUE");
            renderBody(ctx, "Il est formé entre les soussignés une " + formeLabel
                    + ", régie par la loi n° 5-96 du 13 février 1997 relative aux sociétés à"
                    + " responsabilité limitée, telle que modifiée et complétée par les textes subséquents.");
            renderSpacer(ctx, 10);

            // A2 — Dénomination
            renderSectionTitle(ctx, "ARTICLE 2 - DENOMINATION SOCIALE");
            renderBody(ctx, "La dénomination sociale est : " + companyName + ".");
            renderBody(ctx, "Dans tous les actes, factures, annonces, publications et autres documents"
                    + " émanant de la société, la dénomination sociale doit être précédée ou suivie immédiatement"
                    + " des mots \"Société à Responsabilité Limitée\" ou des initiales \"S.A.R.L.\","
                    + " du montant du capital social et de la mention du numéro d'immatriculation au Registre du Commerce.");
            renderSpacer(ctx, 10);

            // A3 — Siège social
            renderSectionTitle(ctx, "ARTICLE 3 - SIEGE SOCIAL");
            String siege = dossier.getAdresseSiege() != null ? dossier.getAdresseSiege() : "En cours de domiciliation";
            renderBody(ctx, "Le siège social est établi à : " + siege + ".");
            renderBody(ctx, "Région : " + (dossier.getRegion() != null ? dossier.getRegion() : "Non précisée") + ".");
            renderBody(ctx, "Il pourra être transféré en tout autre endroit par décision de la gérance, sous réserve"
                    + " des dispositions légales applicables.");
            renderSpacer(ctx, 10);

            // A4 — Objet social
            renderSectionTitle(ctx, "ARTICLE 4 - OBJET SOCIAL");
            String activite = dossier.getDescriptionActivite() != null && !dossier.getDescriptionActivite().isBlank()
                    ? dossier.getDescriptionActivite()
                    : "Activités commerciales, de prestations de services et d'ingénierie.";
            renderBody(ctx, "La société a pour objet social : " + activite + ".");
            renderBody(ctx, "Et généralement, toutes opérations industrielles, commerciales, financières, mobilières"
                    + " ou immobilières pouvant se rattacher directement ou indirectement à l'objet social"
                    + " ou susceptibles d'en faciliter la réalisation ou le développement.");
            renderSpacer(ctx, 10);

            // A5 — Durée
            renderSectionTitle(ctx, "ARTICLE 5 - DUREE DE LA SOCIETE");
            renderBody(ctx, "La durée de la société est fixée à quatre-vingt-dix-neuf (99) ans à compter de la date"
                    + " de son immatriculation au Registre du Commerce, sauf dissolution anticipée ou prorogation.");
            renderSpacer(ctx, 10);

            // A6 — Associés
            renderArticleAssocies(ctx, dossier);

            // A7 — Capital social et tableau de répartition
            renderArticleCapital(ctx, dossier);

            // A8 — Gérance
            renderArticleGerance(ctx, dossier);

            // A9 — Décisions collectives
            renderSectionTitle(ctx, "ARTICLE 9 - DECISIONS COLLECTIVES");
            renderBody(ctx, "Les décisions des associés sont prises en assemblée ou par consultation écrite."
                    + " Chaque part sociale donne droit à une voix.");
            renderBody(ctx, "Les décisions ordinaires sont prises à la majorité simple des parts sociales."
                    + " Les décisions extraordinaires portant modification des statuts sont prises à la majorité"
                    + " des trois quarts des parts sociales.");
            renderSpacer(ctx, 10);

            // A10 — Exercice social
            renderSectionTitle(ctx, "ARTICLE 10 - EXERCICE SOCIAL");
            renderBody(ctx, "L'exercice social commence le premier janvier et se termine le trente et un décembre"
                    + " de chaque année. Par exception, le premier exercice commencera à la date d'immatriculation"
                    + " au Registre du Commerce et se terminera le trente et un décembre de la même année.");
            renderSpacer(ctx, 10);

            // A11 — Résultats et répartition
            renderSectionTitle(ctx, "ARTICLE 11 - REPARTITION DES RESULTATS");
            renderBody(ctx, "Le bénéfice distribuable est constitué par le bénéfice de l'exercice diminué des pertes"
                    + " antérieures et augmenté des reports bénéficiaires. Sur ce bénéfice, il est prélevé cinq pour"
                    + " cent (5%) au moins pour constituer la réserve légale. Ce prélèvement cesse d'être obligatoire"
                    + " lorsque la réserve légale atteint le dixième du capital social.");
            renderBody(ctx, "Le solde est réparti entre les associés proportionnellement au nombre de parts"
                    + " sociales qu'ils possèdent, sauf décision contraire des associés statuant aux conditions"
                    + " requises pour les décisions extraordinaires.");
            renderSpacer(ctx, 10);

            // A12 — Dissolution / Liquidation
            renderSectionTitle(ctx, "ARTICLE 12 - DISSOLUTION ET LIQUIDATION");
            renderBody(ctx, "La société est dissoute dans les cas prévus par la loi ou par décision des associés"
                    + " statuant dans les conditions requises pour les modifications statutaires."
                    + " Lors de la dissolution, la liquidation est effectuée par un ou plusieurs liquidateurs"
                    + " nommés par les associés, qui répartissent le boni de liquidation en proportion des parts"
                    + " de chaque associé.");
            renderSpacer(ctx, 10);

            // A13 — Élection de domicile
            renderSectionTitle(ctx, "ARTICLE 13 - ELECTION DE DOMICILE");
            renderBody(ctx, "Pour l'exécution des présentes, les parties font élection de domicile en leurs"
                    + " demeures et sièges sociaux respectifs tels qu'indiqués dans les présents statuts.");
            renderSpacer(ctx, 10);

            // Mention légale
            ctx.ensureSpace(40);
            renderHorizontalRule(ctx);
            renderLine(ctx, ctx.fontRegular, 8, MARGIN,
                    "Document généré par la plateforme Foundly dans le cadre d'une simulation pédagogique.", 12);
            renderLine(ctx, ctx.fontRegular, 8, MARGIN,
                    "Ce document ne constitue pas un acte authentique et n'a pas été validé par un notaire,", 12);
            renderLine(ctx, ctx.fontRegular, 8, MARGIN,
                    "un avocat ou une administration. La convention de libération à 100% du capital est", 12);
            renderLine(ctx, ctx.fontRegular, 8, MARGIN,
                    "une simplification du scénario Foundly v1 et non une obligation légale générale.", 12);
            if (brouillonIncomplet) {
                renderSpacer(ctx, 4);
                renderLine(ctx, ctx.fontBold, 8, MARGIN,
                        "BROUILLON INCOMPLET — Ce document ne peut pas être utilisé dans un dossier définitif.", 12);
            }

            ctx.close();

            // Page(s) de signatures (multi-pages si > ~6 associés)
            addSignaturePage(doc, dossier, ttfRegular, ttfBold, brouillonIncomplet);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Erreur lors de la generation des statuts SARL PDF : {}", e.getMessage(), e);
            throw new RuntimeException("Echec de la generation du PDF des statuts SARL.", e);
        }
    }

    private void addCoverPage(PDDocument doc, Dossier dossier, PDFont ttfRegular, PDFont ttfBold,
                              boolean brouillonIncomplet) throws IOException {
        PDPage cover = new PDPage(PDRectangle.A4);
        doc.addPage(cover);
        try (PDPageContentStream cs = new PDPageContentStream(doc, cover)) {
            PDFont fontBold    = (ttfBold    != null) ? ttfBold    : new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont fontRegular = (ttfRegular != null) ? ttfRegular : new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font helvBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font helv     = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            // Bandeau supérieur
            cs.setNonStrokingColor(0.1f, 0.3f, 0.6f);
            cs.addRect(0, PAGE_HEIGHT - 90, PAGE_WIDTH, 90);
            cs.fill();
            cs.setNonStrokingColor(1f);
            cs.beginText();
            cs.setFont(helvBold, 20);
            cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 50);
            cs.showText("ROYAUME DU MAROC");
            cs.endText();
            cs.beginText();
            cs.setFont(helv, 12);
            cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 72);
            cs.showText("Plateforme Foundly - Simulation de creation d'entreprise");
            cs.endText();
            cs.setNonStrokingColor(0f);

            // Bandeau BROUILLON INCOMPLET si nécessaire
            if (brouillonIncomplet) {
                cs.setNonStrokingColor(0.9f, 0.1f, 0.1f);
                cs.addRect(0, PAGE_HEIGHT - 115, PAGE_WIDTH, 24);
                cs.fill();
                cs.setNonStrokingColor(1f);
                cs.beginText();
                cs.setFont(fontBold, 11);
                cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 107);
                cs.showText(prepareText(fontBold, "BROUILLON INCOMPLET — Données obligatoires manquantes"));
                cs.endText();
                cs.setNonStrokingColor(0f);
            }

            // Titre principal centré
            float centerY = PAGE_HEIGHT / 2 + 60;
            String titre = "STATUTS CONSTITUTIFS";
            cs.beginText();
            cs.setFont(helvBold, 22);
            float titleWidth = helvBold.getStringWidth(titre) / 1000 * 22;
            cs.newLineAtOffset((PAGE_WIDTH - titleWidth) / 2, centerY);
            cs.showText(titre);
            cs.endText();

            String formeLabel = dossier.getFormeJuridique() == LegalFormCode.SARL_AU
                    ? "Societe a Responsabilite Limitee a Associe Unique"
                    : "Societe a Responsabilite Limitee (SARL)";
            cs.beginText();
            cs.setFont(helv, 14);
            float formeWidth = helv.getStringWidth(formeLabel) / 1000 * 14;
            cs.newLineAtOffset((PAGE_WIDTH - formeWidth) / 2, centerY - 25);
            cs.showText(formeLabel);
            cs.endText();

            // Dénomination sociale (avec TTF pour accents)
            String nom = dossier.getNomEntreprise() != null ? dossier.getNomEntreprise() : "SOCIETE EN FORMATION";
            String nomDisplay = prepareText(fontBold, nom);
            cs.beginText();
            cs.setFont(fontBold, 16);
            // Centrage approximatif (sans accents pour calcul de largeur sur Helvetica)
            float nomWidth = helvBold.getStringWidth(sanitize(nom)) / 1000 * 16;
            float xNom = Math.max(MARGIN, (PAGE_WIDTH - nomWidth) / 2);
            cs.newLineAtOffset(xNom, centerY - 60);
            cs.showText(nomDisplay);
            cs.endText();

            // Ligne de séparation
            cs.setStrokingColor(0.1f, 0.3f, 0.6f);
            cs.setLineWidth(1.5f);
            cs.moveTo(MARGIN, centerY - 80);
            cs.lineTo(PAGE_WIDTH - MARGIN, centerY - 80);
            cs.stroke();
            cs.setStrokingColor(0f);

            // Informations récapitulatives
            float infoY = centerY - 110;
            BigDecimal capital = dossier.getCapitalMad() != null ? dossier.getCapitalMad() : BigDecimal.ZERO;
            drawSimpleTextWithFont(cs, fontRegular, helv, 11, MARGIN, infoY, "Capital social : " + capital + " MAD");
            infoY -= 18;
            String siegeStr = dossier.getAdresseSiege() != null ? dossier.getAdresseSiege() : "Non determine";
            drawSimpleTextWithFont(cs, fontRegular, helv, 11, MARGIN, infoY, "Siege social : " + siegeStr);
            infoY -= 18;
            String regionStr = dossier.getRegion() != null ? dossier.getRegion() : "Non precisee";
            drawSimpleTextWithFont(cs, fontRegular, helv, 11, MARGIN, infoY, "Region : " + regionStr);
            infoY -= 18;
            drawSimpleTextWithFont(cs, fontRegular, helv, 11, MARGIN, infoY,
                    "Reference dossier : " + (dossier.getReference() != null ? dossier.getReference() : "N/A"));

            List<Associe> associesActifs = getAssociesActifs(dossier);
            infoY -= 18;
            drawSimpleTextWithFont(cs, fontRegular, helv, 11, MARGIN, infoY,
                    "Nombre d'associes : " + associesActifs.size());

            // Pied de page
            cs.setNonStrokingColor(0.1f, 0.3f, 0.6f);
            cs.addRect(0, 0, PAGE_WIDTH, 40);
            cs.fill();
            cs.setNonStrokingColor(1f);
            cs.beginText();
            cs.setFont(helv, 8);
            cs.newLineAtOffset(MARGIN, 15);
            cs.showText("Document genere par Foundly - Simulation pedagogique - Non valide juridiquement");
            cs.endText();
            cs.setNonStrokingColor(0f);
        }
    }

    /**
     * Page(s) de signatures multi-pages.
     * 2 blocs par ligne, max 5 lignes par page → 10 associés max par page.
     * Pour 50 associés : 5 pages de signatures.
     */
    private void addSignaturePage(PDDocument doc, Dossier dossier, PDFont ttfRegular, PDFont ttfBold,
                                  boolean brouillonIncomplet) throws IOException {
        List<Associe> associes = getAssociesActifs(dossier);
        if (associes.isEmpty()) {
            // Générer quand même une page de signatures (brouillon)
            associes = new ArrayList<>();
        }

        final float sigBlockWidth  = CONTENT_WIDTH / 2 - 10f;
        final float sigBlockHeight = 90f;  // hauteur d'un bloc : nom + CIN + parts + cadre signature
        final int   COLS           = 2;
        final int   ROWS_PER_PAGE  = 5;
        final int   PER_PAGE       = COLS * ROWS_PER_PAGE;

        PDType1Font helvBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font helv     = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont fBold    = (ttfBold    != null) ? ttfBold    : helvBold;
        PDFont fRegular = (ttfRegular != null) ? ttfRegular : helv;

        int total  = associes.size();
        int pageCount = (total == 0) ? 1 : (int) Math.ceil((double) total / PER_PAGE);

        for (int pageIdx = 0; pageIdx < pageCount; pageIdx++) {
            PDPage sigPage = new PDPage(PDRectangle.A4);
            doc.addPage(sigPage);

            try (PDPageContentStream cs = new PDPageContentStream(doc, sigPage)) {
                float y = TOP_Y;

                // En-tête de la page de signatures
                cs.beginText();
                cs.setFont(fBold, 13);
                cs.newLineAtOffset(MARGIN, y);
                String sigHeader = brouillonIncomplet ? "SIGNATURES DES ASSOCIÉS [BROUILLON INCOMPLET]" : "SIGNATURES DES ASSOCIÉS";
                cs.showText(prepareText(fBold, sigHeader));
                cs.endText();
                y -= 6;

                cs.setStrokingColor(0.3f);
                cs.setLineWidth(0.8f);
                cs.moveTo(MARGIN, y);
                cs.lineTo(MARGIN + CONTENT_WIDTH, y);
                cs.stroke();
                cs.setStrokingColor(0f);
                y -= 16;

                if (pageIdx == 0) {
                    // Préambule uniquement sur la première page de signatures
                    cs.beginText();
                    cs.setFont(helv, 10);
                    cs.newLineAtOffset(MARGIN, y);
                    cs.showText("Les soussignes declarent avoir pris connaissance des presents statuts et les approuver en tous points.");
                    cs.endText();
                    y -= 14;
                    cs.beginText();
                    cs.setFont(helv, 10);
                    cs.newLineAtOffset(MARGIN, y);
                    cs.showText("Fait en autant d'originaux que requis par la loi.");
                    cs.endText();
                    y -= 25;
                }

                // Blocs de signature pour cette page
                int start = pageIdx * PER_PAGE;
                int end   = Math.min(start + PER_PAGE, total);

                int col = 0;
                for (int i = start; i < end; i++) {
                    Associe a = associes.get(i);
                    float xSig = MARGIN + col * (sigBlockWidth + 20f);

                    // Nom et prénom (avec TTF pour accents)
                    String nomPrenom = a.getPrenom() + " " + a.getNom();
                    cs.beginText();
                    cs.setFont(fBold, 9);
                    cs.newLineAtOffset(xSig, y);
                    cs.showText(prepareText(fBold, nomPrenom));
                    cs.endText();

                    // CIN
                    String cin = a.getCinOuPasseport() != null ? a.getCinOuPasseport() : "N/A";
                    cs.beginText();
                    cs.setFont(helv, 9);
                    cs.newLineAtOffset(xSig, y - 13);
                    cs.showText("CIN : " + sanitize(cin));
                    cs.endText();

                    // Parts et pourcentage
                    String parts = "Parts : " + a.getNombreParts() + " (" + formatPct(a.getPourcentageDetention()) + "%)";
                    cs.beginText();
                    cs.setFont(helv, 9);
                    cs.newLineAtOffset(xSig, y - 26);
                    cs.showText(parts);
                    cs.endText();

                    // Cadre "Lu et approuvé"
                    cs.setStrokingColor(0.4f);
                    cs.setLineWidth(0.5f);
                    cs.addRect(xSig, y - 72, sigBlockWidth, 40);
                    cs.stroke();
                    cs.setStrokingColor(0f);

                    cs.beginText();
                    cs.setFont(helv, 8);
                    cs.newLineAtOffset(xSig + 2, y - 75);
                    cs.showText("Signature precedee de \"Lu et approuve\"");
                    cs.endText();

                    col++;
                    if (col == COLS) {
                        col = 0;
                        y -= sigBlockHeight;
                    }
                }

                // Cas dossier sans associés : bloc vide
                if (total == 0) {
                    cs.beginText();
                    cs.setFont(helv, 10);
                    cs.newLineAtOffset(MARGIN, y);
                    cs.showText("(Aucun associe renseigne - BROUILLON INCOMPLET)");
                    cs.endText();
                }

                // Numéro de page en bas
                int sigPageNum = doc.getNumberOfPages();
                cs.beginText();
                cs.setFont(helv, 8);
                cs.newLineAtOffset(PAGE_WIDTH / 2 - 20, 30);
                cs.showText("Page " + sigPageNum);
                cs.endText();
            }
        }
    }

    private void renderArticleAssocies(RenderContext ctx, Dossier dossier) throws IOException {
        List<Associe> associes = getAssociesActifs(dossier);

        renderSectionTitle(ctx, "ARTICLE 6 - ASSOCIES FONDATEURS");

        if (associes.isEmpty()) {
            // Fallback : utiliser l'entrepreneur
            String fondateur = "";
            String cin = "Non renseigne";
            if (dossier.getEntrepreneur() != null) {
                fondateur = dossier.getEntrepreneur().getPrenom() + " " + dossier.getEntrepreneur().getNom();
                cin = dossier.getEntrepreneur().getCin() != null ? dossier.getEntrepreneur().getCin() : "Non renseigne";
            }
            renderBody(ctx, "Associé fondateur unique : " + fondateur + " (CIN : " + cin + ").");
        } else {
            renderBody(ctx, "Les associés fondateurs de la société sont :");
            renderSpacer(ctx, 6);

            int idx = 1;
            for (Associe a : associes) {
                ctx.ensureSpace(55);
                // Nom/prénom avec accents via TTF
                String label = idx + ". " + a.getPrenom() + " " + a.getNom();
                renderLine(ctx, ctx.fontBold, 10, MARGIN + 10, label, 14);
                renderLine(ctx, ctx.fontRegular, 9, MARGIN + 20,
                        "Nationalité : " + (a.getNationalite() != null ? a.getNationalite() : "Non précisée"), 13);
                renderLine(ctx, ctx.fontRegular, 9, MARGIN + 20,
                        "Adresse : " + (a.getAdresse() != null ? a.getAdresse() : "Non précisée"), 13);
                renderLine(ctx, ctx.fontRegular, 9, MARGIN + 20,
                        "Pièce d'identité : " + (a.getCinOuPasseport() != null ? a.getCinOuPasseport() : "Non renseignée"), 13);
                idx++;
            }
        }
        renderSpacer(ctx, 10);
    }

    private void renderArticleCapital(RenderContext ctx, Dossier dossier) throws IOException {
        renderSectionTitle(ctx, "ARTICLE 7 - CAPITAL SOCIAL ET REPARTITION DES PARTS");

        BigDecimal capital = dossier.getCapitalMad() != null ? dossier.getCapitalMad() : BigDecimal.ZERO;
        BigDecimal valeurNominale = dossier.getValeurNominalePart() != null ? dossier.getValeurNominalePart() : new BigDecimal("100");
        Long totalParts = dossier.getNombreTotalParts() != null ? dossier.getNombreTotalParts() : 0L;

        // Référence explicite à l'article 50 de la loi 5-96
        renderBody(ctx, "Conformément à l'article 50 de la loi n° 5-96 sur les sociétés à responsabilité limitée,"
                + " le capital social est fixé à la somme de "
                + capital + " dirhams (MAD), divisé en "
                + totalParts + " parts sociales d'une valeur nominale de "
                + valeurNominale + " dirhams (MAD) chacune, toutes de même catégorie et entièrement souscrites"
                + " par les associés fondateurs.");

        // Libération des apports — Article 51 (correction juridique)
        renderSpacer(ctx, 6);
        renderBody(ctx, "Libération des apports (art. 51 de la loi n° 5-96) :");
        renderBody(ctx, "La loi exige la souscription intégrale du capital et la libération d'au moins un quart (1/4)"
                + " des apports en numéraire lors de la constitution, sans condition de seuil."
                + " Le surplus doit être libéré dans un délai maximal de cinq (5) ans.");

        // Mention blocage bancaire (RG15 : uniquement si capital > 100 000 MAD)
        renderSpacer(ctx, 4);
        if (dossier.isCapitalBlockageRequired()) {
            renderBody(ctx, "Le capital social excédant 100 000 MAD, les fonds libérés à la constitution doivent"
                    + " être déposés dans un compte bancaire bloqué. L'attestation de blocage bancaire"
                    + " (ATTESTATION_BLOCAGE_CAPITAL) représente, dans le cadre du scénario Foundly v1,"
                    + " la totalité du capital déclaré (" + capital + " MAD).");
        } else {
            renderBody(ctx, "Le capital social étant inférieur ou égal à 100 000 MAD, le dépôt en compte"
                    + " bancaire bloqué n'est pas requis. La libération du quart légal reste applicable.");
        }

        renderSpacer(ctx, 4);
        renderBody(ctx, "NB — Convention Foundly v1 : dans le présent scénario de simulation, l'intégralité"
                + " du capital est considérée comme libérée. Cette convention simplifie le parcours pédagogique"
                + " et ne constitue pas une obligation légale générale.");
        renderSpacer(ctx, 10);

        // Tableau de répartition
        List<Associe> associes = getAssociesActifs(dossier);
        if (!associes.isEmpty()) {
            renderLine(ctx, ctx.fontBold, 10, MARGIN, "Tableau de répartition des parts sociales :", 16);

            String[] headers = {"Associé", "Nb parts", "Apport (MAD)", "Détention (%)"};
            float[] colWidths = {200f, 80f, 115f, 100f};
            List<String[]> rows = new ArrayList<>();

            BigDecimal totalApports = BigDecimal.ZERO;
            long totalPartsCheck = 0L;

            for (Associe a : associes) {
                String pct = formatPct(a.getPourcentageDetention());
                rows.add(new String[]{
                        a.getPrenom() + " " + a.getNom(),
                        String.valueOf(a.getNombreParts()),
                        a.getMontantApport() != null ? a.getMontantApport().toPlainString() : "0",
                        pct
                });
                if (a.getMontantApport() != null) totalApports = totalApports.add(a.getMontantApport());
                if (a.getNombreParts() != null) totalPartsCheck += a.getNombreParts();
            }
            // Ligne totaux
            rows.add(new String[]{"TOTAL", String.valueOf(totalPartsCheck), totalApports.toPlainString(), "100"});

            renderTable(ctx, headers, rows, colWidths);
        }
        renderSpacer(ctx, 10);
    }

    private void renderArticleGerance(RenderContext ctx, Dossier dossier) throws IOException {
        renderSectionTitle(ctx, "ARTICLE 8 - GERANCE");

        List<Gerant> gerants = getGerantsActifs(dossier);

        if (gerants.isEmpty()) {
            renderBody(ctx, "La désignation du ou des gérant(s) est prévue dans un acte postérieur.");
            renderSpacer(ctx, 10);
            return;
        }

        // Séparer gérants statutaires et par acte séparé
        List<Gerant> statutaires = gerants.stream()
                .filter(g -> g.getModeNomination() == ModeNominationGerant.STATUTAIRE)
                .toList();
        List<Gerant> acteSepare = gerants.stream()
                .filter(g -> g.getModeNomination() == ModeNominationGerant.ACTE_SEPARE)
                .toList();

        if (!statutaires.isEmpty()) {
            renderBody(ctx, "La société est gérée par " + (statutaires.size() == 1 ? "le gérant" : "les gérants")
                    + " suivant(s), nommé(s) par les présents statuts :");
            renderSpacer(ctx, 6);

            int idx = 1;
            for (Gerant g : statutaires) {
                ctx.ensureSpace(70);
                String label = idx + ". " + g.getPrenom() + " " + g.getNom();
                if (g.isEstAssocie()) label += " (associé)";
                else label += " (tiers, non associé)";
                renderLine(ctx, ctx.fontBold, 10, MARGIN + 10, label, 14);
                renderLine(ctx, ctx.fontRegular, 9, MARGIN + 20,
                        "Nationalité : " + (g.getNationalite() != null ? g.getNationalite() : "Non précisée"), 13);
                renderLine(ctx, ctx.fontRegular, 9, MARGIN + 20,
                        "Adresse : " + (g.getAdresse() != null ? g.getAdresse() : "Non précisée"), 13);
                renderLine(ctx, ctx.fontRegular, 9, MARGIN + 20,
                        "Pièce d'identité : " + (g.getCinOuPasseport() != null ? g.getCinOuPasseport() : "Non renseignée"), 13);
                String duree = g.getDureeMandat() != null ? g.getDureeMandat() : "Indéterminée";
                renderLine(ctx, ctx.fontRegular, 9, MARGIN + 20,
                        "Durée du mandat : " + duree, 13);
                idx++;
            }

            renderSpacer(ctx, 6);
            renderBody(ctx, "Le(s) gérant(s) est/sont investi(s) des pouvoirs les plus étendus pour agir en toute"
                    + " circonstance au nom de la société, dans la limite de l'objet social et sous réserve"
                    + " des pouvoirs attribués par la loi aux associés. Il(s) engage(nt) la société par"
                    + " sa/leur signature.");
        }

        if (!acteSepare.isEmpty()) {
            renderSpacer(ctx, 8);
            renderBody(ctx, "Le(s) gérant(s) suivant(s) sera/seront nommé(s) par acte séparé, conformément"
                    + " à l'article 63 de la loi n° 5-96 :");
            renderSpacer(ctx, 6);
            int idx = 1;
            for (Gerant g : acteSepare) {
                ctx.ensureSpace(50);
                String label = idx + ". " + g.getPrenom() + " " + g.getNom();
                if (g.isEstAssocie()) label += " (associé)";
                else label += " (tiers, non associé)";
                renderLine(ctx, ctx.fontBold, 10, MARGIN + 10, label, 14);
                renderLine(ctx, ctx.fontRegular, 9, MARGIN + 20,
                        "Nomination : Par procès-verbal d'assemblée générale ultérieur.", 13);
                idx++;
            }
        }

        renderSpacer(ctx, 10);
    }

    // =========================================================================
    // Fallback statuts simples (Auto-entrepreneur, etc.)
    // =========================================================================

    private byte[] generateStatutsSimplesPdf(Dossier dossier) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                float y = TOP_Y;
                drawSimpleText(cs, fontBold, 16, MARGIN, y, "ROYAUME DU MAROC");
                y -= 20;
                drawSimpleText(cs, fontBold, 14, MARGIN, y, "STATUTS CONSTITUTIFS TYPE");
                y -= 18;
                drawSimpleText(cs, fontRegular, 10, MARGIN, y, "Modele de demonstration genere par Foundly");
                y -= 25;

                String nom = dossier.getNomEntreprise() != null ? dossier.getNomEntreprise() : "SOCIETE EN FORMATION";
                String forme = dossier.getFormeJuridique() != null ? dossier.getFormeJuridique().name() : "";
                drawSimpleText(cs, fontBold, 14, MARGIN, y, sanitize(nom + " (" + forme + ")"));
                y -= 25;

                drawSimpleText(cs, fontBold, 11, MARGIN, y, "ARTICLE 1 - FORME JURIDIQUE");
                y -= 15;
                drawSimpleText(cs, fontRegular, 10, MARGIN, y, "Il est forme une entreprise individuelle regie par la legislation marocaine en vigueur.");
                y -= 25;

                drawSimpleText(cs, fontBold, 11, MARGIN, y, "ARTICLE 2 - DENOMINATION");
                y -= 15;
                drawSimpleText(cs, fontRegular, 10, MARGIN, y, "La denomination est : " + sanitize(nom));
                y -= 25;

                drawSimpleText(cs, fontBold, 11, MARGIN, y, "ARTICLE 3 - SIEGE ET REGION");
                y -= 15;
                String siege = dossier.getAdresseSiege() != null ? dossier.getAdresseSiege() : "En cours de domiciliation";
                drawSimpleText(cs, fontRegular, 10, MARGIN, y, "Siege : " + sanitize(siege));
                y -= 14;
                drawSimpleText(cs, fontRegular, 10, MARGIN, y, "Region : " + sanitize(dossier.getRegion() != null ? dossier.getRegion() : ""));
                y -= 25;

                drawSimpleText(cs, fontBold, 11, MARGIN, y, "ARTICLE 4 - DECLARANT");
                y -= 15;
                if (dossier.getEntrepreneur() != null) {
                    String declarant = dossier.getEntrepreneur().getPrenom() + " " + dossier.getEntrepreneur().getNom();
                    String cin = dossier.getEntrepreneur().getCin() != null ? dossier.getEntrepreneur().getCin() : "Non renseigne";
                    drawSimpleText(cs, fontRegular, 10, MARGIN, y, "Declarant : " + sanitize(declarant) + " — CIN : " + sanitize(cin));
                }
                y -= 35;
                drawSimpleText(cs, fontBold, 10, MARGIN + 280, y, "Fait a " + sanitize(dossier.getRegion() != null ? dossier.getRegion() : ""));
                y -= 15;
                drawSimpleText(cs, fontRegular, 9, MARGIN + 280, y, "Signature du declarant");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Erreur generation statuts simples : {}", e.getMessage(), e);
            throw new RuntimeException("Echec generation statuts.", e);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<Associe> getAssociesActifs(Dossier dossier) {
        if (dossier.getAssocies() == null) return List.of();
        return dossier.getAssocies().stream().filter(Associe::isActif).toList();
    }

    private List<Gerant> getGerantsActifs(Dossier dossier) {
        if (dossier.getGerants() == null) return List.of();
        return dossier.getGerants().stream().filter(Gerant::isActif).toList();
    }

    private String formatPct(BigDecimal pct) {
        if (pct == null) return "0.00";
        return pct.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Dessin simple sans contexte (utilisé pour cover page et signature page) — polices Standard-14 uniquement. */
    private void drawSimpleText(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(text));
        cs.endText();
    }

    /**
     * Dessin avec police TTF (si disponible) ou fallback Standard-14.
     * Si TTF : accents conservés. Sinon : sanitize().
     */
    private void drawSimpleTextWithFont(PDPageContentStream cs, PDFont ttfFont, PDType1Font fallback,
                                        float size, float x, float y, String text) throws IOException {
        cs.beginText();
        if (ttfFont != null) {
            cs.setFont(ttfFont, size);
            cs.newLineAtOffset(x, y);
            cs.showText(prepareText(ttfFont, text));
        } else {
            cs.setFont(fallback, size);
            cs.newLineAtOffset(x, y);
            cs.showText(sanitize(text));
        }
        cs.endText();
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
        // Les polices Standard 14 de PDFBox n'encodent pas tous les caractères Unicode
        // (par ex. le tiret cadratin « — » présent dans le reçu). La normalisation ici
        // protège chaque ligne du PDF, y compris les textes fixes du reçu de paiement.
        cs.showText(sanitize(text));
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
