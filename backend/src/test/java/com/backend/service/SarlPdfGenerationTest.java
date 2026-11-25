package com.backend.service;

import com.backend.entity.Associe;
import com.backend.entity.Dossier;
import com.backend.entity.Gerant;
import com.backend.entity.Utilisateur;
import com.backend.enums.LegalFormCode;
import com.backend.enums.ModeNominationGerant;
import com.backend.service.impl.PdfGenerationServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de génération PDF des statuts SARL — Phase 3 et Phase 3.1.
 *
 * Couvre :
 *  T1  — SARL 2 associés + 1 gérant statutaire associé
 *  T2  — SARL 3 associés + 2 gérants (1 statutaire + 1 acte séparé)
 *  T3  — SARL 50 associés (stress test pagination)
 *  T4  — Gérant associé correctement identifié
 *  T5  — Gérant tiers correctement identifié
 *  T6  — Montants et répartition des parts cohérents dans le texte
 *  T7  — Signatures présentes pour tous les associés
 *  T8  — Pagination automatique (> 1 page pour 50 associés)
 *  T9  — Pas de crash / contenu non tronqué (fichier > 0 bytes, PDF valide)
 *  T10 — Dossier avec données minimales manquantes : génération sans exception
 *  T11 — Non-régression : AE statuts simples toujours générés (1 page)
 *  T12 — Non-régression : formulaire CRI non affecté
 *  T13 — Données manquantes → mention BROUILLON INCOMPLET dans le PDF
 *  T13b— Dossier complet → pas de mention BROUILLON INCOMPLET
 *  T14 — Conservation des caractères accentués (police TTF ou fallback)
 *  T15 — Noms longs : PDF valide sans crash
 *  T16 — Signatures 50 associés : tous les noms présents
 *  T17 — Pages de signatures : nombre suffisant pour 50 associés (>= 7 pages)
 */
class SarlPdfGenerationTest {

    private PdfGenerationServiceImpl pdfService;
    private Utilisateur entrepreneur;

    @BeforeEach
    void setUp() {
        pdfService = new PdfGenerationServiceImpl();

        entrepreneur = new Utilisateur();
        entrepreneur.setId(UUID.randomUUID());
        entrepreneur.setPrenom("Yassine");
        entrepreneur.setNom("El Amrani");
        entrepreneur.setCin("AB123456");
        entrepreneur.setEmail("yassine@foundly.ma");
        entrepreneur.setTelephone("0661234567");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Dossier buildSarlDossier(String nom, BigDecimal capital, BigDecimal valeurNominale) {
        Dossier d = new Dossier();
        d.setId(UUID.randomUUID());
        d.setReference("FND-2026-TEST");
        d.setNomEntreprise(nom);
        d.setFormeJuridique(LegalFormCode.SARL);
        d.setRegion("Casablanca-Settat");
        d.setCapitalMad(capital);
        d.setValeurNominalePart(valeurNominale);
        d.setNombreTotalParts(capital.divide(valeurNominale).longValue());
        d.setDescriptionActivite("Developpement de solutions numeriques innovantes.");
        d.setAdresseSiege("12 Boulevard d'Anfa, Casablanca");
        d.setCapitalBlockageRequired(capital.compareTo(new BigDecimal("100000")) > 0);
        d.setEntrepreneur(entrepreneur);
        d.setAssocies(new ArrayList<>());
        d.setGerants(new ArrayList<>());
        return d;
    }

    private Associe makeAssocie(Dossier d, String prenom, String nom, String cin, long parts, BigDecimal apport) {
        BigDecimal pct = apport.multiply(new BigDecimal("100")).divide(d.getCapitalMad(), 2, java.math.RoundingMode.HALF_UP);
        Associe a = new Associe(d, nom, prenom, cin, "Marocaine", "Casablanca", parts, apport, pct);
        a.setId(UUID.randomUUID());
        a.setActif(true);
        return a;
    }

    private Gerant makeGerantAssocie(Dossier d, Associe a, ModeNominationGerant mode) {
        Gerant g = new Gerant(d, a, true, a.getNom(), a.getPrenom(), a.getCinOuPasseport(),
                "Marocaine", "Casablanca", mode, "Indeterminee", null);
        g.setId(UUID.randomUUID());
        g.setActif(true);
        return g;
    }

    private Gerant makeGerantTiers(Dossier d, String prenom, String nom, String cin, ModeNominationGerant mode) {
        Gerant g = new Gerant(d, null, false, nom, prenom, cin, "Marocaine", "Rabat", mode, "3 ans", null);
        g.setId(UUID.randomUUID());
        g.setActif(true);
        return g;
    }

    private String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private int countPages(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return doc.getNumberOfPages();
        }
    }

    // =========================================================================
    // T1 — SARL 2 associés + 1 gérant statutaire associé
    // =========================================================================

    @Test
    @DisplayName("T1 — SARL 2 associes + 1 gerant statutaire : PDF valide et contenu correct")
    void t1_sarl2AssociesGerantStatutaire() throws IOException {
        Dossier d = buildSarlDossier("Atlas Tech SARL", new BigDecimal("50000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Yassine", "El Amrani", "AB123456", 300L, new BigDecimal("30000"));
        Associe a2 = makeAssocie(d, "Omar", "Benjelloun", "CD789012", 200L, new BigDecimal("20000"));
        d.getAssocies().addAll(List.of(a1, a2));

        Gerant g1 = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g1);

        byte[] pdf = pdfService.generateStatutsPdf(d);

        assertNotNull(pdf);
        assertTrue(pdf.length > 1000, "Le PDF ne doit pas etre vide");

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertTrue(doc.getNumberOfPages() >= 2, "SARL doit avoir au moins 2 pages (page de garde + corps)");
        }

        String text = extractText(pdf);
        assertTrue(text.contains("Responsabilite Limitee") || text.contains("Responsabilité Limitée"), "Doit mentionner la forme SARL");
        assertTrue(text.contains("Atlas Tech SARL"), "Doit contenir le nom de la societe");
        assertTrue(text.contains("50000"), "Doit mentionner le capital");
        assertTrue(text.contains("El Amrani") || text.contains("Amrani"), "Doit contenir l'associe 1");
        assertTrue(text.contains("Benjelloun"), "Doit contenir l'associe 2");
        assertTrue(text.contains("AB123456"), "Doit contenir la CIN de l'associe 1");
        assertTrue(text.contains("300") || text.contains("200"), "Doit mentionner le nombre de parts");
        assertTrue(text.contains("gerant") || text.contains("gérant") || text.contains("GERANCE"), "Doit mentionner la gerance");
        assertTrue(text.contains("Anfa"), "Doit mentionner le siege social");
        assertTrue(text.contains("approuve") || text.contains("approuvé") || text.contains("Signature"), "Doit contenir la section signatures");
    }

    // =========================================================================
    // T2 — SARL 3 associés + 2 gérants (1 statutaire + 1 acte séparé)
    // =========================================================================

    @Test
    @DisplayName("T2 — SARL 3 associes + 2 gerants (statutaire + acte separe) : modes de nomination corrects")
    void t2_sarl3AssociesGerantsMixtes() throws IOException {
        Dossier d = buildSarlDossier("Maghreb Digital SARL", new BigDecimal("150000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Yassine", "El Amrani", "AB123456", 900L, new BigDecimal("90000"));
        Associe a2 = makeAssocie(d, "Omar", "Benjelloun", "CD789012", 300L, new BigDecimal("30000"));
        Associe a3 = makeAssocie(d, "Leila", "Tahiri", "EF345678", 300L, new BigDecimal("30000"));
        d.getAssocies().addAll(List.of(a1, a2, a3));

        Gerant g1 = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        Gerant g2 = makeGerantTiers(d, "Mohamed", "Alami", "GH567890", ModeNominationGerant.ACTE_SEPARE);
        d.getGerants().addAll(List.of(g1, g2));

        byte[] pdf = pdfService.generateStatutsPdf(d);

        assertNotNull(pdf);
        assertTrue(pdf.length > 1000);

        String text = extractText(pdf);

        assertTrue(text.contains("El Amrani") || text.contains("Amrani"), "Associe 1 manquant");
        assertTrue(text.contains("Benjelloun"), "Associe 2 manquant");
        assertTrue(text.contains("Tahiri"), "Associe 3 manquant");
        assertTrue(text.contains("100 000") || text.contains("100000") || text.contains("attestation") || text.contains("blocage"),
                "Doit mentionner l'attestation bancaire pour capital > 100 000");
        assertTrue(text.contains("statuts") || text.contains("nomme") || text.contains("gerant") || text.contains("gérant"),
                "Doit mentionner nomination statutaire");
        assertTrue(text.contains("acte") || text.contains("ulterieur") || text.contains("ultérieur") || text.contains("Alami"),
                "Doit mentionner nomination par acte separe");
        assertTrue(text.contains("associe") || text.contains("associé"),
                "Doit distinguer gerant associe");
        assertTrue(text.contains("tiers") || text.contains("non associe") || text.contains("non associé"),
                "Doit distinguer gerant tiers");
    }

    // =========================================================================
    // T3 — SARL 50 associés (stress test pagination)
    // =========================================================================

    @Test
    @DisplayName("T3 — SARL 50 associes : pagination automatique, PDF valide sans crash")
    void t3_sarl50Associes() throws IOException {
        Dossier d = buildSarlDossier("Grand Corp SARL", new BigDecimal("500000"), new BigDecimal("100"));

        int nbAssocies = 50;
        List<Associe> associes = new ArrayList<>();
        for (int i = 1; i <= nbAssocies; i++) {
            String prenom = "Prenom" + i;
            String nom = "Nom" + i;
            String cin = String.format("CIN%06d", i);
            Associe a = makeAssocie(d, prenom, nom, cin, 100L, new BigDecimal("10000"));
            associes.add(a);
        }
        d.setAssocies(associes);
        Gerant g = makeGerantAssocie(d, associes.get(0), ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        byte[] pdf = pdfService.generateStatutsPdf(d);

        assertNotNull(pdf);
        assertTrue(pdf.length > 5000, "Un PDF de 50 associes doit peser plus de 5 Ko");

        int pages = countPages(pdf);
        assertTrue(pages >= 4, "Un PDF de 50 associes doit avoir au moins 4 pages (pagination)");

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertNotNull(doc);
        }

        String text = extractText(pdf);
        assertTrue(text.contains("Nom1"), "Premier associe manquant");
        assertTrue(text.contains("Nom50"), "Dernier associe manquant");
    }

    // =========================================================================
    // T4 — Gérant associé correctement identifié
    // =========================================================================

    @Test
    @DisplayName("T4 — Gerant associe : identifie comme associe dans les statuts")
    void t4_gerantAssocie() throws IOException {
        Dossier d = buildSarlDossier("Tech Maroc SARL", new BigDecimal("20000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Karima", "Idrissi", "KI111222", 120L, new BigDecimal("12000"));
        Associe a2 = makeAssocie(d, "Rachid", "Oualid", "RO333444", 80L, new BigDecimal("8000"));
        d.getAssocies().addAll(List.of(a1, a2));

        Gerant g = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        byte[] pdf = pdfService.generateStatutsPdf(d);
        String text = extractText(pdf);

        assertTrue(text.contains("associe") || text.contains("associé"), "Le gerant associe doit etre identifie comme associe");
        assertTrue(text.contains("Idrissi"), "Le nom du gerant associe doit apparaitre");
    }

    // =========================================================================
    // T5 — Gérant tiers correctement identifié
    // =========================================================================

    @Test
    @DisplayName("T5 — Gerant tiers : identifie comme non associe dans les statuts")
    void t5_gerantTiers() throws IOException {
        Dossier d = buildSarlDossier("Sud Invest SARL", new BigDecimal("30000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Hassan", "Zahraoui", "HZ555666", 180L, new BigDecimal("18000"));
        Associe a2 = makeAssocie(d, "Nadia", "Fassi", "NF777888", 120L, new BigDecimal("12000"));
        d.getAssocies().addAll(List.of(a1, a2));

        Gerant gTiers = makeGerantTiers(d, "Karim", "Chaoui", "KC999000", ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(gTiers);

        byte[] pdf = pdfService.generateStatutsPdf(d);
        String text = extractText(pdf);

        assertTrue(text.contains("tiers") || text.contains("non associe") || text.contains("non associé"),
                "Le gerant tiers doit etre identifie comme non associe");
        assertTrue(text.contains("Chaoui"), "Le nom du gerant tiers doit apparaitre");
        assertTrue(text.contains("Zahraoui"), "L'associe 1 doit aussi apparaitre");
    }

    // =========================================================================
    // T6 — Vérification des montants et de la répartition des parts
    // =========================================================================

    @Test
    @DisplayName("T6 — Montants et repartition des parts : tableau coherent dans le PDF")
    void t6_montantsEtRepartition() throws IOException {
        Dossier d = buildSarlDossier("Capital Exact SARL", new BigDecimal("10000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Ali", "Maarouf", "AM111", 60L, new BigDecimal("6000"));
        Associe a2 = makeAssocie(d, "Sara", "Bennis", "SB222", 40L, new BigDecimal("4000"));
        d.getAssocies().addAll(List.of(a1, a2));

        Gerant g = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        byte[] pdf = pdfService.generateStatutsPdf(d);
        String text = extractText(pdf);

        assertTrue(text.contains("10000"), "Doit mentionner le capital total 10000");
        assertTrue(text.contains("100"), "Doit mentionner 100 parts au total");
        assertTrue(text.contains("6000") || text.contains("4000"), "Doit mentionner les apports individuels");
        assertTrue(text.contains("Maarouf"), "Associe 1 doit figurer dans le tableau");
        assertTrue(text.contains("Bennis"), "Associe 2 doit figurer dans le tableau");
        assertTrue(text.contains("TOTAL"), "Doit avoir une ligne TOTAL");
    }

    // =========================================================================
    // T7 — Vérification des signatures de tous les associés
    // =========================================================================

    @Test
    @DisplayName("T7 — Signatures : section signatures presente pour tous les associes")
    void t7_signatures() throws IOException {
        Dossier d = buildSarlDossier("Sign Corp SARL", new BigDecimal("20000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Jamal", "Kettani", "JK001", 120L, new BigDecimal("12000"));
        Associe a2 = makeAssocie(d, "Fatiha", "Lemrabet", "FL002", 80L, new BigDecimal("8000"));
        d.getAssocies().addAll(List.of(a1, a2));

        Gerant g = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        byte[] pdf = pdfService.generateStatutsPdf(d);
        String text = extractText(pdf);

        assertTrue(text.contains("SIGNATURES") || text.contains("Signatures"), "Doit avoir une section signatures");
        assertTrue(text.contains("approuve") || text.contains("approuvé"), "Doit contenir la mention 'Lu et approuve'");
        assertTrue(text.contains("Kettani"), "Associe 1 doit signer");
        assertTrue(text.contains("Lemrabet"), "Associe 2 doit signer");
    }

    // =========================================================================
    // T8 — Vérification de la pagination automatique
    // =========================================================================

    @Test
    @DisplayName("T8 — Pagination : numero de page present, > 1 page pour SARL standard")
    void t8_pagination() throws IOException {
        Dossier d = buildSarlDossier("Paginated SARL", new BigDecimal("50000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Adam", "Fassi", "AF001", 300L, new BigDecimal("30000"));
        Associe a2 = makeAssocie(d, "Zineb", "Chraibi", "ZC002", 200L, new BigDecimal("20000"));
        d.getAssocies().addAll(List.of(a1, a2));

        Gerant g = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        byte[] pdf = pdfService.generateStatutsPdf(d);

        int pages = countPages(pdf);
        assertTrue(pages >= 3, "SARL standard doit avoir au moins 3 pages (garde + corps + signatures). Trouvees : " + pages);

        String text = extractText(pdf);
        assertTrue(text.contains("Page"), "Doit contenir les numeros de page");
    }

    // =========================================================================
    // T9 — Pas de crash / contenu non tronqué (fichier valide)
    // =========================================================================

    @Test
    @DisplayName("T9 — Robustesse : PDF valide sans contenu tronque ni crash")
    void t9_robustessePasDeCrash() throws IOException {
        Dossier d = buildSarlDossier("Société-Innovation & Tech (Maroc) SARL",
                new BigDecimal("75000"), new BigDecimal("100"));
        d.setDescriptionActivite("Développement, conception et commercialisation de logiciels, "
                + "solutions d'intelligence artificielle, services Cloud, conseil en transformation "
                + "numérique, formation professionnelle, et toutes activités connexes ou complémentaires "
                + "pouvant contribuer directement ou indirectement à la réalisation de l'objet social "
                + "principal tel que défini ci-dessus, dans les limites autorisées par la législation marocaine.");

        for (int i = 1; i <= 5; i++) {
            Associe a = makeAssocie(d, "Prénom" + i, "Nom" + i, "CIN" + i, 100L, new BigDecimal("15000"));
            d.getAssocies().add(a);
        }
        Gerant g = makeGerantTiers(d, "Directeur", "General", "DG001", ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        assertDoesNotThrow(() -> {
            byte[] pdf = pdfService.generateStatutsPdf(d);
            assertNotNull(pdf);
            assertTrue(pdf.length > 2000);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                assertTrue(doc.getNumberOfPages() > 0);
            }
        });
    }

    // =========================================================================
    // T10 — Dossier avec données minimales manquantes
    // =========================================================================

    @Test
    @DisplayName("T10 — Donnees manquantes : generation sans exception, valeurs par defaut utilisees")
    void t10_donneesMinimaManquantes() throws IOException {
        Dossier d = new Dossier();
        d.setId(UUID.randomUUID());
        d.setReference("FND-MINIMAL");
        d.setNomEntreprise("Minimal SARL");
        d.setFormeJuridique(LegalFormCode.SARL);
        d.setRegion("Rabat-Sale-Kenitra");
        d.setCapitalMad(new BigDecimal("10000"));
        d.setValeurNominalePart(new BigDecimal("100"));
        d.setNombreTotalParts(100L);
        d.setCapitalBlockageRequired(false);
        d.setEntrepreneur(entrepreneur);
        d.setAssocies(null);
        d.setGerants(null);

        assertDoesNotThrow(() -> {
            byte[] pdf = pdfService.generateStatutsPdf(d);
            assertNotNull(pdf);
            assertTrue(pdf.length > 500);
            String text = extractText(pdf);
            assertTrue(text.contains("Minimal SARL"), "Doit contenir la denomination");
        });
    }

    // =========================================================================
    // T11 — Non-régression Auto-Entrepreneur (statuts simplifiés)
    // =========================================================================

    @Test
    @DisplayName("T11 — Non-regression AE : statuts simples generes sur 1 page")
    void t11_nonRegressionAutoEntrepreneur() throws IOException {
        Dossier d = new Dossier();
        d.setId(UUID.randomUUID());
        d.setReference("FND-AE-001");
        d.setNomEntreprise("Karim Tech Auto");
        d.setFormeJuridique(LegalFormCode.AUTO_ENTREPRENEUR);
        d.setRegion("Marrakech-Safi");
        d.setCapitalMad(BigDecimal.ZERO);
        d.setCapitalBlockageRequired(false);
        d.setDescriptionActivite("Conseil en informatique");
        d.setAdresseSiege("Rue Oued Tensift, Marrakech");
        d.setEntrepreneur(entrepreneur);

        byte[] pdf = pdfService.generateStatutsPdf(d);

        assertNotNull(pdf);
        assertTrue(pdf.length > 500);

        int pages = countPages(pdf);
        assertEquals(1, pages, "Le statut AE doit generer exactement 1 page");

        String text = extractText(pdf);
        assertTrue(text.contains("AUTO_ENTREPRENEUR") || text.contains("Karim Tech Auto"),
                "Doit contenir la denomination AE");
    }

    // =========================================================================
    // T12 — Non-régression Formulaire CRI (non affecté)
    // =========================================================================

    @Test
    @DisplayName("T12 — Non-regression formulaire CRI : toujours genere sur 1 page")
    void t12_nonRegressionFormulaireCri() throws IOException {
        Dossier d = buildSarlDossier("CRI Test SARL", new BigDecimal("40000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Reda", "Mansouri", "RM001", 400L, new BigDecimal("40000"));
        d.getAssocies().add(a1);

        byte[] pdf = pdfService.generateFormulaireUniqueCriPdf(d);

        assertNotNull(pdf);
        assertTrue(pdf.length > 500);

        int pages = countPages(pdf);
        assertEquals(1, pages, "Le formulaire CRI doit toujours etre sur 1 page");

        String text = extractText(pdf);
        assertTrue(text.contains("CRI") || text.contains("CENTRE REGIONAL"), "Doit contenir l'entete CRI");
        assertTrue(text.contains("CRI Test SARL"), "Doit contenir le nom de la societe");
    }

    // =========================================================================
    // T13 — Données obligatoires manquantes → mention BROUILLON INCOMPLET
    // =========================================================================

    @Test
    @DisplayName("T13 — Donnees manquantes : PDF contient mention BROUILLON INCOMPLET")
    void t13_brouillonIncomplet_mentionDansPdf() throws IOException {
        // SARL sans nomEntreprise, sans associés, sans gérant
        Dossier d = new Dossier();
        d.setId(UUID.randomUUID());
        d.setReference("FND-INCOMPLET");
        // nomEntreprise null → données manquantes
        d.setFormeJuridique(LegalFormCode.SARL);
        d.setRegion("Rabat-Sale-Kenitra");
        d.setCapitalMad(new BigDecimal("20000"));
        d.setValeurNominalePart(new BigDecimal("100"));
        d.setNombreTotalParts(200L);
        d.setCapitalBlockageRequired(false);
        d.setEntrepreneur(entrepreneur);
        d.setAssocies(new ArrayList<>());  // 0 associé → incomplet pour SARL (min 2)
        d.setGerants(new ArrayList<>());   // 0 gérant → incomplet

        byte[] pdf = pdfService.generateStatutsPdf(d);
        assertNotNull(pdf);
        assertTrue(pdf.length > 500);

        String text = extractText(pdf);
        // La mention BROUILLON INCOMPLET doit apparaître au moins une fois
        assertTrue(text.contains("BROUILLON INCOMPLET"),
                "Le PDF doit contenir 'BROUILLON INCOMPLET' quand des données sont manquantes. Texte trouvé (500 premiers chars) : "
                        + text.substring(0, Math.min(500, text.length())));
    }

    @Test
    @DisplayName("T13b — Dossier SARL complet : aucune mention BROUILLON INCOMPLET")
    void t13b_dossierComplet_pasDeMentionBrouillon() throws IOException {
        Dossier d = buildSarlDossier("Tech Complet SARL", new BigDecimal("50000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Yassine", "El Amrani", "AB123456", 300L, new BigDecimal("30000"));
        Associe a2 = makeAssocie(d, "Omar", "Benjelloun", "CD789012", 200L, new BigDecimal("20000"));
        d.getAssocies().addAll(List.of(a1, a2));
        Gerant g1 = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g1);

        byte[] pdf = pdfService.generateStatutsPdf(d);
        String text = extractText(pdf);

        assertFalse(text.contains("BROUILLON INCOMPLET"),
                "Un dossier complet ne doit pas porter la mention BROUILLON INCOMPLET");
    }

    // =========================================================================
    // T14 — Conservation des caractères accentués (police TTF ou fallback)
    // =========================================================================

    @Test
    @DisplayName("T14 — Accents conserves : noms avec accents lisibles dans le PDF")
    void t14_accentsConservesDansPdf() throws IOException {
        Dossier d = buildSarlDossier("Societe Francaise Innovation", new BigDecimal("30000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Élodie", "Réseau", "EL001", 150L, new BigDecimal("15000"));
        Associe a2 = makeAssocie(d, "Rémy", "Désirée", "RD002", 150L, new BigDecimal("15000"));
        d.getAssocies().addAll(List.of(a1, a2));

        Gerant g = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        // Ne doit pas crasher, quelle que soit la police disponible
        assertDoesNotThrow(() -> {
            byte[] pdf = pdfService.generateStatutsPdf(d);
            assertNotNull(pdf);
            assertTrue(pdf.length > 1000);

            String text = extractText(pdf);

            // Le nom doit apparaître : soit accentué (TTF), soit dégradé (Helvetica)
            // "Élodie" → "Elodie" ou "lodie" ou "lodie " selon la dégradation
            boolean hasElodie = text.contains("lodie") || text.contains("Élodie") || text.contains("Elodie");
            assertTrue(hasElodie, "Le prénom Élodie (ou variante) doit être présent dans le PDF");

            if (text.contains("Élodie") || text.contains("Rémy")) {
                System.out.println("T14 : Police TTF active — accents préservés ✓");
            } else {
                System.out.println("T14 : Police TTF absente ou fallback Helvetica — accents dégradés (sans crash) ✓");
            }
        });
    }

    // =========================================================================
    // T15 — Noms longs : PDF valide sans crash
    // =========================================================================

    @Test
    @DisplayName("T15 — Noms longs : PDF valide sans crash ni tronquage bloquant")
    void t15_nomsLongsSansTronquageBrutal() throws IOException {
        Dossier d = buildSarlDossier("Societe Internationale de Commerce SARL",
                new BigDecimal("100000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Abderrahmane", "El Mansouri-Benabdellah", "AM001", 600L, new BigDecimal("60000"));
        Associe a2 = makeAssocie(d, "Sara", "Bennis", "SB002", 400L, new BigDecimal("40000"));
        d.getAssocies().addAll(List.of(a1, a2));

        Gerant g = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        assertDoesNotThrow(() -> {
            byte[] pdf = pdfService.generateStatutsPdf(d);
            assertNotNull(pdf);
            assertTrue(pdf.length > 1000);

            String text = extractText(pdf);
            // Le nom (ou une partie) doit apparaître dans les articles (pas seulement dans le tableau tronqué)
            assertTrue(text.contains("Mansouri") || text.contains("Abderrahmane"),
                    "Le nom long doit apparaître dans le PDF (éventuellement tronqué dans le tableau, mais présent dans les articles)");
        });
    }

    // =========================================================================
    // T16 — 50 associés : tous présents dans les pages de signatures
    // =========================================================================

    @Test
    @DisplayName("T16 — Signatures 50 associes : tous les noms presents dans le PDF")
    void t16_signatures50AssociesTousPresents() throws IOException {
        Dossier d = buildSarlDossier("Grand Corp 50 SARL", new BigDecimal("500000"), new BigDecimal("100"));

        int nbAssocies = 50;
        List<Associe> associes = new ArrayList<>();
        for (int i = 1; i <= nbAssocies; i++) {
            String prenom = "Prenom" + i;
            String nom    = "Famille" + i;
            String cin    = String.format("SIG%06d", i);
            Associe a = makeAssocie(d, prenom, nom, cin, 100L, new BigDecimal("10000"));
            associes.add(a);
        }
        d.setAssocies(associes);

        Gerant g = makeGerantAssocie(d, associes.get(0), ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        byte[] pdf = pdfService.generateStatutsPdf(d);
        assertNotNull(pdf);

        String text = extractText(pdf);

        List<String> manquants = new ArrayList<>();
        for (int i = 1; i <= nbAssocies; i++) {
            String nom = "Famille" + i;
            if (!text.contains(nom)) {
                manquants.add(nom);
            }
        }
        assertTrue(manquants.isEmpty(),
                "Les associés suivants sont absents du PDF (signatures) : " + manquants);
    }

    // =========================================================================
    // T17 — 50 associés : nombre de pages ≥ 7
    // =========================================================================

    @Test
    @DisplayName("T17 — Pages signatures 50 associes : nombre de pages suffisant (>= 7)")
    void t17_pagesSignatures50Associes() throws IOException {
        Dossier d = buildSarlDossier("Paginated Sig SARL", new BigDecimal("500000"), new BigDecimal("100"));

        int nbAssocies = 50;
        List<Associe> associes = new ArrayList<>();
        for (int i = 1; i <= nbAssocies; i++) {
            Associe a = makeAssocie(d, "P" + i, "N" + i, String.format("T%06d", i), 100L, new BigDecimal("10000"));
            associes.add(a);
        }
        d.setAssocies(associes);
        Gerant g = makeGerantAssocie(d, associes.get(0), ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        byte[] pdf = pdfService.generateStatutsPdf(d);

        int pages = countPages(pdf);
        // 50 associés / 10 par page = 5 pages de signatures + 1 de garde + au moins 2 de corps = 8+
        // On accepte >= 7 pour être robuste
        assertTrue(pages >= 7,
                "Un PDF de 50 associés doit avoir au moins 7 pages. Trouvées : " + pages
                + " (1 garde + corps + 5 pages de signatures attendues)");

        System.out.println("T17 : " + pages + " pages générées pour 50 associés");
    }

    // =========================================================================
    // T18 — Donnée manquante ciblée : Siège social
    // =========================================================================

    @Test
    @DisplayName("T18 — Siege social manquant : mention BROUILLON INCOMPLET et reference au siege")
    void t18_siegeSocialManquant() throws IOException {
        Dossier d = buildSarlDossier("No Siege SARL", new BigDecimal("50000"), new BigDecimal("100"));
        d.setAdresseSiege(null); // siège manquant

        Associe a1 = makeAssocie(d, "Yassine", "El Amrani", "AB123456", 300L, new BigDecimal("30000"));
        Associe a2 = makeAssocie(d, "Omar", "Benjelloun", "CD789012", 200L, new BigDecimal("20000"));
        d.getAssocies().addAll(List.of(a1, a2));
        Gerant g = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        byte[] pdf = pdfService.generateStatutsPdf(d);
        assertNotNull(pdf);
        String text = extractText(pdf);

        assertTrue(text.contains("BROUILLON INCOMPLET"), "Doit porter la mention BROUILLON INCOMPLET");
        assertTrue(text.contains("siège social") || text.contains("siege") || text.contains("adresseSiege"),
                "Doit lister l'adresse du siège social comme donnée manquante");
    }

    // =========================================================================
    // T19 — Donnée manquante ciblée : Objet social
    // =========================================================================

    @Test
    @DisplayName("T19 — Objet social manquant : mention BROUILLON INCOMPLET et reference a l'activite")
    void t19_objetSocialManquant() throws IOException {
        Dossier d = buildSarlDossier("No Objet SARL", new BigDecimal("50000"), new BigDecimal("100"));
        d.setDescriptionActivite(""); // objet social vide

        Associe a1 = makeAssocie(d, "Yassine", "El Amrani", "AB123456", 300L, new BigDecimal("30000"));
        Associe a2 = makeAssocie(d, "Omar", "Benjelloun", "CD789012", 200L, new BigDecimal("20000"));
        d.getAssocies().addAll(List.of(a1, a2));
        Gerant g = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        byte[] pdf = pdfService.generateStatutsPdf(d);
        assertNotNull(pdf);
        String text = extractText(pdf);

        assertTrue(text.contains("BROUILLON INCOMPLET"), "Doit porter la mention BROUILLON INCOMPLET");
        assertTrue(text.contains("Objet social") || text.contains("descriptionActivite") || text.contains("activité"),
                "Doit lister l'objet social comme donnée manquante");
    }

    // =========================================================================
    // T20 — Donnée manquante ciblée : Identité incomplète d'un associé (CIN manquante)
    // =========================================================================

    @Test
    @DisplayName("T20 — Identite associe incomplete : CIN manquante -> BROUILLON INCOMPLET")
    void t20_identiteAssocieIncomplete() throws IOException {
        Dossier d = buildSarlDossier("Bad Associe SARL", new BigDecimal("50000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Yassine", "El Amrani", "AB123456", 300L, new BigDecimal("30000"));
        Associe a2 = makeAssocie(d, "Omar", "Benjelloun", null, 200L, new BigDecimal("20000")); // CIN manquante
        d.getAssocies().addAll(List.of(a1, a2));
        Gerant g = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        byte[] pdf = pdfService.generateStatutsPdf(d);
        assertNotNull(pdf);
        String text = extractText(pdf);

        assertTrue(text.contains("BROUILLON INCOMPLET"), "Doit porter la mention BROUILLON INCOMPLET");
        assertTrue(text.contains("CIN") || text.contains("passeport"),
                "Doit signaler la CIN manquante de l'associé");
    }

    // =========================================================================
    // T21 — Incohérence ciblée : Apports et parts incohérents
    // =========================================================================

    @Test
    @DisplayName("T21 — Incoherence apports et parts : somme apports != capital -> BROUILLON INCOMPLET")
    void t21_apportsIncoherents() throws IOException {
        Dossier d = buildSarlDossier("Incoherent SARL", new BigDecimal("50000"), new BigDecimal("100"));

        // 300 parts x 100 = 30000, mais déclaré à 25000 (incohérence individuelle et globale)
        Associe a1 = makeAssocie(d, "Yassine", "El Amrani", "AB123456", 300L, new BigDecimal("25000"));
        Associe a2 = makeAssocie(d, "Omar", "Benjelloun", "CD789012", 200L, new BigDecimal("20000"));
        d.getAssocies().addAll(List.of(a1, a2));
        Gerant g = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        d.getGerants().add(g);

        byte[] pdf = pdfService.generateStatutsPdf(d);
        assertNotNull(pdf);
        String text = extractText(pdf);

        assertTrue(text.contains("BROUILLON INCOMPLET"), "Doit porter la mention BROUILLON INCOMPLET");
        assertTrue(text.contains("Incohérence") || text.contains("incohérent") || text.contains("capital"),
                "Doit signaler l'incohérence des apports");
    }

    // =========================================================================
    // T22 — Donnée manquante ciblée : Identité gérant statutaire sans mandat
    // =========================================================================

    @Test
    @DisplayName("T22 — Gerant statutaire sans mandat : BROUILLON INCOMPLET")
    void t22_gerantStatutaireSansMandat() throws IOException {
        Dossier d = buildSarlDossier("Gerant No Mandat SARL", new BigDecimal("50000"), new BigDecimal("100"));

        Associe a1 = makeAssocie(d, "Yassine", "El Amrani", "AB123456", 300L, new BigDecimal("30000"));
        Associe a2 = makeAssocie(d, "Omar", "Benjelloun", "CD789012", 200L, new BigDecimal("20000"));
        d.getAssocies().addAll(List.of(a1, a2));

        Gerant g = makeGerantAssocie(d, a1, ModeNominationGerant.STATUTAIRE);
        g.setDureeMandat(""); // mandat vide
        d.getGerants().add(g);

        byte[] pdf = pdfService.generateStatutsPdf(d);
        assertNotNull(pdf);
        String text = extractText(pdf);

        assertTrue(text.contains("BROUILLON INCOMPLET"), "Doit porter la mention BROUILLON INCOMPLET");
        assertTrue(text.contains("durée du mandat") || text.contains("mandat") || text.contains("Gérant"),
                "Doit signaler la durée du mandat manquante");
    }

    // =========================================================================
    // T23 — Génération et sauvegarde des fichiers PDF de vérification visuelle (2, 3 et 50 associés)
    // =========================================================================

    @Test
    @DisplayName("T23 — Sauvegarde des PDFs pour verification visuelle (2, 3 et 50 associes)")
    void t23_genererFichiersVisualisation() throws IOException {
        java.io.File outputDir = new java.io.File("target/test-pdf-output");
        outputDir.mkdirs();

        // 1. Scénario 2 associés
        Dossier d2 = buildSarlDossier("Atlas Innovation SARL", new BigDecimal("40000"), new BigDecimal("100"));
        Associe a1 = makeAssocie(d2, "Yassine", "El Amrani", "AB123456", 240L, new BigDecimal("24000"));
        Associe a2 = makeAssocie(d2, "Khadija", "Tazi", "CD789012", 160L, new BigDecimal("16000"));
        d2.getAssocies().addAll(List.of(a1, a2));
        d2.getGerants().add(makeGerantAssocie(d2, a1, ModeNominationGerant.STATUTAIRE));
        byte[] pdf2 = pdfService.generateStatutsPdf(d2);
        java.nio.file.Files.write(new java.io.File(outputDir, "statuts_sarl_2_associes.pdf").toPath(), pdf2);

        // 2. Scénario 3 associés
        Dossier d3 = buildSarlDossier("Maghreb Global Solutions SARL", new BigDecimal("150000"), new BigDecimal("100"));
        Associe b1 = makeAssocie(d3, "Driss", "Chraïbi", "DC111222", 750L, new BigDecimal("75000"));
        Associe b2 = makeAssocie(d3, "Leïla", "Benmoussa", "LB333444", 450L, new BigDecimal("45000"));
        Associe b3 = makeAssocie(d3, "Mehdi", "Tahiri", "MT555666", 300L, new BigDecimal("30000"));
        d3.getAssocies().addAll(List.of(b1, b2, b3));
        d3.getGerants().add(makeGerantAssocie(d3, b1, ModeNominationGerant.STATUTAIRE));
        d3.getGerants().add(makeGerantTiers(d3, "Nawal", "El Fassi", "NF999888", ModeNominationGerant.ACTE_SEPARE));
        byte[] pdf3 = pdfService.generateStatutsPdf(d3);
        java.nio.file.Files.write(new java.io.File(outputDir, "statuts_sarl_3_associes.pdf").toPath(), pdf3);

        // 3. Scénario 50 associés
        Dossier d50 = buildSarlDossier("Consortium Maroc 50 SARL", new BigDecimal("500000"), new BigDecimal("100"));
        List<Associe> associes50 = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Associe as = makeAssocie(d50, "Prénom" + i, "Nom" + i, String.format("CIN%06d", i), 100L, new BigDecimal("10000"));
            associes50.add(as);
        }
        d50.setAssocies(associes50);
        d50.getGerants().add(makeGerantAssocie(d50, associes50.get(0), ModeNominationGerant.STATUTAIRE));
        byte[] pdf50 = pdfService.generateStatutsPdf(d50);
        java.nio.file.Files.write(new java.io.File(outputDir, "statuts_sarl_50_associes.pdf").toPath(), pdf50);

        int p2 = countPages(pdf2);
        int p3 = countPages(pdf3);
        int p50 = countPages(pdf50);

        assertTrue(p2 >= 3, "2 associés >= 3 pages");
        assertTrue(p3 >= 3, "3 associés >= 3 pages");
        assertTrue(p50 >= 7, "50 associés >= 7 pages");

        System.out.println("T23 : Fichiers PDF générés avec succès :");
        System.out.println(" - statuts_sarl_2_associes.pdf  : " + p2 + " pages (" + pdf2.length + " octets)");
        System.out.println(" - statuts_sarl_3_associes.pdf  : " + p3 + " pages (" + pdf3.length + " octets)");
        System.out.println(" - statuts_sarl_50_associes.pdf : " + p50 + " pages (" + pdf50.length + " octets)");
    }
}
