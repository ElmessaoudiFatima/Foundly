package com.backend.service.impl;

import com.backend.dto.publicinfo.*;
import com.backend.enums.LegalFormCode;
import com.backend.service.PublicService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class PublicServiceImpl implements PublicService {

    @Override
    public List<LegalFormDto> getLegalForms() {
        List<LegalFormDto> list = new ArrayList<>();

        list.add(new LegalFormDto(
                LegalFormCode.AUTO_ENTREPRENEUR,
                "Auto-Entrepreneur",
                "Idéal pour démarrer seul sans capital et avec une fiscalité ultra-simplifiée.",
                "Régime encadré par la loi n°114-13. Démarche 100% allégée, dispense de tenue de comptabilité complexe et dispense d'immatriculation au Registre de Commerce.",
                BigDecimal.ZERO,
                1,
                1,
                false,
                false,
                false,
                BigDecimal.ZERO,
                2,
                List.of("Aucun capital minimum", "Pas de statuts ni de frais de constitution", "Fiscalité libératoire réduite (0.5% ou 1%)", "Exonération de taxe professionnelle pendant 5 ans"),
                List.of("Copie CIN ou carte de séjour", "Photo d'identité", "Formulaire d'inscription RNAE")
        ));

        list.add(new LegalFormDto(
                LegalFormCode.SARL_AU,
                "SARL à Associé Unique (SARL-AU)",
                "La forme société la plus prisée au Maroc pour les entrepreneurs individuels.",
                "Société unipersonnelle offrant une totale séparation des patrimoines personnel et professionnel. Représente 64,8% des personnes morales constituées au Maroc.",
                BigDecimal.ZERO,
                1,
                1,
                true,
                true,
                true,
                new BigDecimal("1250.00"),
                3,
                List.of("Patrimoine personnel protégé à hauteur des apports", "Capital librement fixé", "Crédibilité maximale auprès des banques et clients", "Transformation ultérieure en SARL pluripersonnelle simplifiée"),
                List.of("CIN du gérant/associé unique", "Justificatif de siège (bail/domiciliation)", "Statuts constitutifs signés", "Attestation de blocage si capital > 100 000 MAD")
        ));

        list.add(new LegalFormDto(
                LegalFormCode.SARL,
                "Société à Responsabilité Limitée (SARL)",
                "Le standard incontournable pour s'associer entre 2 et 50 partenaires.",
                "Régie par la loi n°5-96 modifiée. Structure modulable, protection des associés, gouvernance collégiale ou par gérant nommé.",
                BigDecimal.ZERO,
                2,
                50,
                true,
                true,
                true,
                new BigDecimal("1450.00"),
                3,
                List.of("Responsabilité des associés limitée aux apports", "Aucun capital minimum légal", "Gérance assurée par un associé ou un tiers", "Cadre juridique robuste et sécurisant"),
                List.of("CIN de tous les associés et du gérant", "Justificatif de siège", "Statuts constitutifs", "PV de nomination de la gérance si hors statuts", "Attestation bancaire si capital > 100 000 MAD")
        ));

        list.add(new LegalFormDto(
                LegalFormCode.SA,
                "Société Anonyme (SA)",
                "Conçue pour les projets d'envergure nécessitant des capitaux importants.",
                "Société de capitaux par excellence (loi n°17-95). Capital minimum légal de 300 000 MAD, gouvernance par Conseil d'Administration ou Directoire.",
                new BigDecimal("300000.00"),
                5,
                null,
                true,
                true,
                true,
                new BigDecimal("3500.00"),
                7,
                List.of("Levée de capitaux et entrée d'investisseurs institutionnels", "Facilité de cession des actions", "Statut prestigieux pour grands comptes et marchés publics"),
                List.of("CIN de l'ensemble des administrateurs et actionnaires", "Attestation bancaire obligatoire de blocage du 1/4 du capital minimum", "Statuts notariés ou sous seing privé", "Rapport du commissaire aux comptes")
        ));

        return list;
    }

    @Override
    public AssistantRecommendationDto recommendLegalForm(AssistantRequestDto request) {
        int partners = request.numberOfPartners() != null ? request.numberOfPartners() : 1;
        boolean limited = Boolean.TRUE.equals(request.wantsLimitedLiability());
        BigDecimal capital = request.estimatedCapitalMad() != null ? request.estimatedCapitalMad() : BigDecimal.ZERO;

        LegalFormCode recommended;
        List<LegalFormCode> alternatives = new ArrayList<>();
        String rationale;
        List<String> checklist;
        String capitalAdvice;
        String taxSummary;

        if (partners == 1) {
            if (limited || capital.compareTo(new BigDecimal("50000")) > 0 || Boolean.TRUE.equals(request.intendsToHireEmployees())) {
                recommended = LegalFormCode.SARL_AU;
                alternatives.add(LegalFormCode.AUTO_ENTREPRENEUR);
                rationale = "La SARL à Associé Unique (SARL-AU) est idéale pour votre situation : elle protège intégralement votre patrimoine personnel tout en vous offrant une totale liberté de gestion et la possibilité de recruter des collaborateurs.";
                checklist = List.of("Certificat négatif OMPIC", "Statuts types rédigés", "Bail commercial ou contrat de domiciliation", "CIN de l'associé unique", capital.compareTo(new BigDecimal("100000")) > 0 ? "Attestation bancaire de blocage de capital" : "Dispense d'attestation de blocage");
                capitalAdvice = capital.compareTo(new BigDecimal("100000")) > 0
                        ? "Votre capital projeté (" + capital + " MAD) dépasse 100 000 MAD : le blocage bancaire auprès de votre agence est obligatoire avant dépôt au CRI."
                        : "Votre capital (" + capital + " MAD) est <= 100 000 MAD : vous bénéficiez de la dispense légale de blocage bancaire préalable !";
                taxSummary = "Imposition à l'IS (Impôt sur les Sociétés) avec barème progressif (10% à 20%) et déductibilité des charges d'exploitation.";
            } else {
                recommended = LegalFormCode.AUTO_ENTREPRENEUR;
                alternatives.add(LegalFormCode.SARL_AU);
                rationale = "Le statut Auto-Entrepreneur est optimal pour démarrer seul sans frais de constitution, sans statuts et avec une fiscalité forfaitaire ultra-allégée.";
                checklist = List.of("Copie CIN", "Formulaire RNAE", "Photo d'identité");
                capitalAdvice = "Aucun apport en capital n'est requis par la loi.";
                taxSummary = "Prélèvement fiscal libératoire très faible (0.5% pour les activités industrielles/commerciales, 1% pour les prestations de services).";
            }
        } else {
            // Plusieurs associés
            if (partners >= 5 && capital.compareTo(new BigDecimal("300000")) >= 0) {
                recommended = LegalFormCode.SA;
                alternatives.add(LegalFormCode.SARL);
                rationale = "Compte tenu du nombre important d'associés (" + partners + ") et du capital (" + capital + " MAD >= 300 000 MAD), la Société Anonyme (SA) permet d'accueillir des investisseurs et d'émettre des actions librement négociables.";
                checklist = List.of("Certificat négatif OMPIC", "Statuts notariés ou SSP", "Attestation bancaire de blocage du capital social", "CIN de tous les actionnaires", "PV de constitution");
                capitalAdvice = "Le blocage bancaire de 25% minimum du capital initial est légalement requis lors de la souscription.";
                taxSummary = "Régime de l'IS classique avec obligations comptables complètes et nomination obligatoire d'un commissaire aux comptes.";
            } else {
                recommended = LegalFormCode.SARL;
                alternatives.add(LegalFormCode.SARL_AU);
                rationale = "La SARL classique est la recommandation par excellence : elle sécurise les " + partners + " associés en limitant leurs risques à leurs apports respectifs, sans imposer de capital minimum légal.";
                checklist = List.of("Certificat négatif OMPIC", "Statuts signés par tous les associés", "Justificatif de siège social", "CIN des associés et du gérant", capital.compareTo(new BigDecimal("100000")) > 0 ? "Attestation bancaire de blocage" : "Dispense de blocage bancaire");
                capitalAdvice = capital.compareTo(new BigDecimal("100000")) > 0
                        ? "Capital > 100 000 MAD : dépôt en compte bloqué bancaire requis."
                        : "Capital <= 100 000 MAD : dispense légale de blocage bancaire préalable.";
                taxSummary = "Imposition à l'IS au niveau de la société, dividendes soumis à la retenue à la source (15%).";
            }
        }

        String label = switch (recommended) {
            case AUTO_ENTREPRENEUR -> "Auto-Entrepreneur";
            case SARL_AU -> "SARL à Associé Unique";
            case SARL -> "Société à Responsabilité Limitée";
            case SA -> "Société Anonyme";
        };

        return new AssistantRecommendationDto(
                recommended,
                label,
                alternatives,
                rationale,
                checklist,
                capitalAdvice,
                taxSummary,
                "Résultat indicatif généré par l'assistant Foundly à des fins d'orientation, ne constituant pas une consultation juridique formelle."
        );
    }

    @Override
    public SimulationDto simulate(SimulationRequestDto request) {
        LegalFormCode code = request.legalFormCode();
        BigDecimal capital = request.capitalAmountMad() != null ? request.capitalAmountMad() : BigDecimal.ZERO;

        BigDecimal totalCost = BigDecimal.ZERO;
        int duration = 3;
        boolean blockageRequired = false;
        BigDecimal adminFees = BigDecimal.ZERO;
        BigDecimal pubFees = BigDecimal.ZERO;
        List<String> steps = List.of();

        switch (code) {
            case AUTO_ENTREPRENEUR -> {
                totalCost = BigDecimal.ZERO;
                duration = 2;
                blockageRequired = false;
                adminFees = BigDecimal.ZERO;
                pubFees = BigDecimal.ZERO;
                steps = List.of(
                        "1. Vérification d'éligibilité CRI",
                        "2. Formulaire RNAE",
                        "3. Validation d'identité Barid Al-Maghrib / CRI",
                        "4. Délivrance de la carte Auto-Entrepreneur"
                );
            }
            case SARL_AU, SARL -> {
                blockageRequired = capital.compareTo(new BigDecimal("100000")) > 0;
                adminFees = new BigDecimal("432.76"); // 232.76 MAD OMPIC + 200 MAD greffe
                pubFees = new BigDecimal("800.00");   // Annonces légales + Bulletin officiel
                totalCost = adminFees.add(pubFees);
                duration = 3;
                steps = List.of(
                        "1. Certificat négatif (OMPIC)",
                        "2. Statuts constitutifs signés",
                        blockageRequired ? "3. Blocage bancaire du capital (Attestation requise)" : "3. Blocage du capital (Dispensé)",
                        "4. Dépôt et contrôle CRI",
                        "5. Immatriculation Registre de Commerce (Tribunal)",
                        "6. Identifiant fiscal et taxe professionnelle (DGI)",
                        "7. Affiliation CNSS",
                        "8. Publication légale (JAL & BO)"
                );
            }
            case SA -> {
                blockageRequired = true;
                adminFees = new BigDecimal("1200.00");
                pubFees = new BigDecimal("1800.00");
                totalCost = adminFees.add(pubFees);
                duration = 7;
                steps = List.of(
                        "1. Certificat négatif (OMPIC)",
                        "2. Rédaction des statuts notariés",
                        "3. Blocage bancaire obligatoire du capital (min. 75 000 MAD)",
                        "4. Dépôt au guichet unique CRI",
                        "5. Immatriculation RC (Tribunal de Commerce)",
                        "6. Fiscalité & Patente DGI",
                        "7. Affiliation CNSS",
                        "8. Double publication JAL et Bulletin Officiel"
                );
            }
        }

        String label = switch (code) {
            case AUTO_ENTREPRENEUR -> "Auto-Entrepreneur";
            case SARL_AU -> "SARL à Associé Unique";
            case SARL -> "Société à Responsabilité Limitée";
            case SA -> "Société Anonyme";
        };

        return new SimulationDto(
                code,
                label,
                totalCost,
                duration,
                blockageRequired,
                adminFees,
                pubFees,
                steps,
                "Estimation financière et de délai indicative selon les barèmes réglementaires marocains en vigueur en 2026."
        );
    }

    @Override
    public List<FaqItemDto> getFaqs() {
        return List.of(
                new FaqItemDto("faq-1", "Démarches Générales",
                        "Quel est le rôle du certificat négatif délivré par l'OMPIC ?",
                        "Le certificat négatif est le document officiel attestant qu'aucun autre opérateur n'utilise la dénomination commerciale souhaitée. Il est délivré sous 24 à 48h, coûte 232,76 MAD TTC et reste valable pendant 90 jours (3 mois)."),

                new FaqItemDto("faq-2", "Capital Social",
                        "Quand le blocage du capital social auprès d'une banque est-il obligatoire ?",
                        "Conformément à la loi marocaine modifiée n°5-96, pour les SARL et SARL-AU dont le capital social est inférieur ou égal à 100 000 MAD, le blocage bancaire n'est plus obligatoire. Il demeure strictement obligatoire pour les SA et pour toute SARL dont le capital excède 100 000 MAD (RG15)."),

                new FaqItemDto("faq-3", "Guichet Unique CRI",
                        "Quels sont les documents obligatoires pour déposer mon dossier au CRI ?",
                        "Pour une société, vous devez fournir la CIN des associés, le justificatif de domiciliation ou contrat de bail légalisé, les statuts signés, et l'attestation bancaire si le capital dépasse 100 000 MAD. Foundly génère automatiquement vos statuts types et votre formulaire unique pré-remplis."),

                new FaqItemDto("faq-4", "Paiement & Frais",
                        "Les paiements effectués sur Foundly sont-ils réels ?",
                        "Non, Foundly est une plateforme pédagogique et de démonstration. Tous les règlements de frais administratifs (certificat négatif, immatriculation) sont intégralement simulés, sans aucune transaction bancaire réelle."),

                new FaqItemDto("faq-5", "Rejets & Corrections",
                        "Que se passe-t-il si un agent rejette une étape de mon dossier ?",
                        "En cas de rejet par un agent administratif (OMPIC, CRI, Tribunal, etc.), vous recevez une notification immédiate précisant le motif obligatoire du rejet. Votre dossier passe au statut 'À corriger' ; vous pouvez remplacer le document incriminé en un clic et re-soumettre pour reprendre le traitement sans délai (RG8).")
        );
    }
}
