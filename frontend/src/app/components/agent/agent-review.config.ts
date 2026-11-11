export interface AgentReviewField {
  key: string;
  label: string;
  type: 'text' | 'date' | 'file' | 'checkbox';
  required: boolean;
}

export interface AgentChecklistItem {
  key: string;
  label: string;
}

export interface AgentReviewConfig {
  title: string;
  checklist: AgentChecklistItem[];
  resultTitle: string;
  resultFields: AgentReviewField[];
  companyFields: string[];
  documentTypes: string[];
  capitalChecklist: boolean;
}

const commonCompanyFields = ['denomination', 'legalForm', 'activity', 'entrepreneur', 'cin', 'region', 'capital', 'contact'];

export const AGENT_REVIEW_CONFIG: Record<string, AgentReviewConfig> = {
  OMPIC: {
    title: 'Office Marocain de la Propriété Industrielle et Commerciale',
    checklist: [
      { key: 'nameAvailability', label: 'La dénomination est disponible et conforme.' },
      { key: 'applicantIdentity', label: 'L’identité du demandeur correspond aux pièces fournies.' },
      { key: 'documentsComplete', label: 'Les pièces et informations nécessaires sont complètes.' }
    ],
    resultTitle: 'Certificat négatif délivré',
    resultFields: [
      { key: 'reservedName', label: 'Dénomination réservée', type: 'text', required: true },
      { key: 'issuedAt', label: 'Date de délivrance', type: 'date', required: true },
      { key: 'certificateNumber', label: 'Numéro du certificat', type: 'text', required: true }
    ],
    companyFields: commonCompanyFields,
    documentTypes: ['CIN', 'JUSTIFICATIF_SIEGE', 'AUTRE'],
    capitalChecklist: false
  },
  CRI: {
    title: "Centre Régional d'Investissement",
    checklist: [
      { key: 'companyFileComplete', label: 'Le dossier de création est complet.' },
      { key: 'companyDetailsConsistent', label: 'Les données de l’entreprise sont cohérentes.' },
      { key: 'documentsComplete', label: 'Les documents requis sont présents et lisibles.' }
    ],
    resultTitle: 'Accusé de dépôt CRI',
    resultFields: [
      { key: 'depositReceiptNumber', label: 'Référence de l’accusé de dépôt', type: 'text', required: true },
      { key: 'capitalAmountConfirmed', label: 'Montant du capital conforme', type: 'checkbox', required: true },
      { key: 'companyNameConfirmed', label: 'Dénomination conforme', type: 'checkbox', required: true },
      { key: 'partnersIdentityConfirmed', label: 'Identité des associés conforme', type: 'checkbox', required: true }
    ],
    companyFields: commonCompanyFields,
    documentTypes: ['CIN', 'JUSTIFICATIF_SIEGE', 'STATUTS_SIGNES', 'ATTESTATION_BLOCAGE_CAPITAL', 'FORMULAIRE_UNIQUE', 'PV_NOMINATION_GERANT', 'AUTRE'],
    capitalChecklist: true
  },
  TRIBUNAL: {
    title: 'Tribunal de commerce',
    checklist: [
      { key: 'companyNameChecked', label: 'La dénomination figure sur les statuts signés.' },
      { key: 'statutesCompliant', label: 'Les statuts et justificatifs sont conformes.' },
      { key: 'registrationComplete', label: 'La demande d’immatriculation est complète.' }
    ],
    resultTitle: 'Immatriculation au Registre du Commerce',
    resultFields: [
      { key: 'commercialRegisterNumber', label: 'Numéro RC', type: 'text', required: true },
      { key: 'ice', label: 'ICE', type: 'text', required: true }
    ],
    companyFields: commonCompanyFields.filter(field => field !== 'contact'),
    documentTypes: ['CIN', 'JUSTIFICATIF_SIEGE', 'STATUTS_SIGNES', 'FORMULAIRE_UNIQUE', 'PV_NOMINATION_GERANT', 'AUTRE'],
    capitalChecklist: false
  },
  DGI: {
    title: 'Direction Générale des Impôts',
    checklist: [
      { key: 'taxIdentityVerified', label: 'Les informations fiscales sont vérifiées.' },
      { key: 'activityTaxChecked', label: 'L’activité et la taxe professionnelle sont vérifiées.' },
      { key: 'supportingDocuments', label: 'Les justificatifs fiscaux sont complets.' }
    ],
    resultTitle: 'Identification fiscale',
    resultFields: [
      { key: 'taxIdentifier', label: 'Identifiant fiscal', type: 'text', required: true },
      { key: 'professionalTaxCertificate', label: 'Attestation de taxe professionnelle', type: 'text', required: true }
    ],
    companyFields: commonCompanyFields.filter(field => field !== 'contact'),
    documentTypes: ['CIN', 'JUSTIFICATIF_SIEGE', 'FORMULAIRE_UNIQUE', 'AUTRE'],
    capitalChecklist: false
  },
  CNSS: {
    title: 'Caisse Nationale de Sécurité Sociale',
    checklist: [
      { key: 'employerInformationVerified', label: 'Les informations de l’employeur sont vérifiées.' },
      { key: 'legalStatusVerified', label: 'La forme juridique et les justificatifs sont conformes.' },
      { key: 'affiliationRequestComplete', label: 'La demande d’affiliation est complète.' }
    ],
    resultTitle: 'Affiliation CNSS',
    resultFields: [
      { key: 'affiliationNumber', label: 'Numéro d’affiliation', type: 'text', required: true }
    ],
    companyFields: ['denomination', 'legalForm', 'activity', 'entrepreneur', 'cin', 'region', 'capital', 'contact'],
    documentTypes: ['CIN', 'STATUTS_SIGNES', 'FORMULAIRE_UNIQUE', 'AUTRE'],
    capitalChecklist: false
  },
  JOURNAL: {
    title: "Journal d'annonces légales",
    checklist: [
      { key: 'publicationTextVerified', label: 'Le texte de l’annonce correspond aux statuts.' },
      { key: 'publicationDetailsVerified', label: 'Les références et dates de publication sont exactes.' },
      { key: 'publicationProofReady', label: 'La preuve de parution est lisible et complète.' }
    ],
    resultTitle: 'Attestation de parution',
    resultFields: [
      { key: 'publicationCertificate', label: 'Attestation de parution (document requis)', type: 'file', required: true }
    ],
    companyFields: ['denomination', 'legalForm', 'entrepreneur', 'region'],
    documentTypes: ['CERTIFICAT_NEGATIF', 'STATUTS_SIGNES', 'FORMULAIRE_UNIQUE', 'ATTESTATION_PARUTION', 'AUTRE'],
    capitalChecklist: false
  }
};

export const WORKFLOW_STEP_LABELS: Record<string, string> = {
  CERTIFICAT_NEGATIF: 'Certificat négatif',
  REDACTION_STATUTS: 'Statuts',
  BLOCAGE_CAPITAL: 'Blocage du capital',
  DEPOT_CRI: 'Dépôt CRI',
  REGISTRE_COMMERCE: 'Registre de commerce',
  IDENTIFIANT_FISCAL: 'DGI',
  AFFILIATION_CNSS: 'CNSS',
  PUBLICATION_LEGALE: 'Publication légale',
  VERIFICATION_ELIGIBILITE: 'Vérification d’éligibilité',
  FORMULAIRE_INSCRIPTION: 'Inscription',
  VALIDATION_POSTE_MAROC: 'Validation en agence',
  DELIVRANCE_CARTE: 'Délivrance de la carte'
};
