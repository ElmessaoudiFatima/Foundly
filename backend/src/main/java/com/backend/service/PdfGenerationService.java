package com.backend.service;

import com.backend.entity.Dossier;
import com.backend.entity.Paiement;

public interface PdfGenerationService {

    byte[] generateStatutsPdf(Dossier dossier);

    byte[] generateFormulaireUniqueCriPdf(Dossier dossier);

    byte[] generatePaymentReceiptPdf(Paiement paiement);
}
