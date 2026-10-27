package com.backend.service;

import com.backend.entity.Dossier;
import com.backend.entity.Paiement;
import com.backend.entity.SuiviEtape;
import com.backend.entity.Utilisateur;
import com.backend.enums.LegalFormCode;
import com.backend.enums.PaymentStatus;
import com.backend.service.impl.PdfGenerationServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PdfGenerationServiceImplTest {

    private PdfGenerationServiceImpl pdfService;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        pdfService = new PdfGenerationServiceImpl();

        Utilisateur entrepreneur = new Utilisateur();
        entrepreneur.setPrenom("Karim");
        entrepreneur.setNom("Bennani");
        entrepreneur.setCin("BE123456");
        entrepreneur.setEmail("karim.bennani@test.ma");
        entrepreneur.setTelephone("0612345678");

        dossier = new Dossier();
        dossier.setId(UUID.randomUUID());
        dossier.setReference("FND-2026-00042");
        dossier.setNomEntreprise("Maghreb Tech Solutions");
        dossier.setFormeJuridique(LegalFormCode.SARL);
        dossier.setRegion("Casablanca-Settat");
        dossier.setCapitalMad(new BigDecimal("100000"));
        dossier.setDescriptionActivite("Développement de solutions numériques innovantes");
        dossier.setAdresseSiege("Boulevard d'Anfa, Casablanca");
        dossier.setEntrepreneur(entrepreneur);
    }

    @Test
    @DisplayName("Génération des Statuts types PDF valides avec PDFBox")
    void shouldGenerateValidStatutsPdf() throws IOException {
        byte[] pdfBytes = pdfService.generateStatutsPdf(dossier);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "Le PDF des statuts généré ne doit pas être vide");

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("Génération du Formulaire Unique CRI en PDF valide")
    void shouldGenerateValidFormulaireUniquePdf() throws IOException {
        byte[] pdfBytes = pdfService.generateFormulaireUniqueCriPdf(dossier);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500);

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("Génération du reçu de paiement simulé en PDF valide")
    void shouldGenerateValidPaymentReceiptPdf() throws IOException {
        SuiviEtape step = new SuiviEtape();
        step.setLibelle("Certificat négatif (OMPIC)");

        Paiement p = new Paiement();
        p.setDossier(dossier);
        p.setSuiviEtape(step);
        p.setMontantMad(new BigDecimal("232.76"));
        p.setReferenceSimulation("SIM-FND-AB12CD");
        p.setStatut(PaymentStatus.CONFIRME);
        p.setMethode("CARTE_BANCAIRE_SIMULEE");
        p.setConfirmedAt(Instant.now());

        byte[] pdfBytes = pdfService.generatePaymentReceiptPdf(p);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500);

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }
}
