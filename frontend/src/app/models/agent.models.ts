/**
 * Modèles TypeScript pour l'espace agent administratif.
 * Réf. CDC § 8.2 — Interface Agent Administratif.
 */

export type LegalFormCode = 'SARL' | 'SARL_AU' | 'SA' | 'AUTO_ENTREPRENEUR';
export type DossierStatus = 'BROUILLON' | 'SOUMIS' | 'EN_COURS' | 'A_CORRIGER' | 'FINALISE';
export type StepStatus = 'EN_ATTENTE' | 'EN_COURS' | 'VALIDEE' | 'REJETEE';
export type ActorType = 'ENTREPRENEUR' | 'AGENT' | 'ADMINISTRATEUR' | 'SYSTEME';

/** Élément de la file d'attente des dossiers à traiter */
export interface AgentQueueItem {
  dossierId: string;
  dossierReference: string;
  nomEntreprise: string;
  formeJuridique: LegalFormCode;
  statutDossier: DossierStatus;
  etapeCode: string;
  etapeLibelle: string;
  organismeCode: string;
  etapeStartedAt: string | null;
  dossierSubmittedAt: string | null;
  entrepreneurNom?: string | null;
  entrepreneurEmail?: string | null;
  indicativeDurationDays?: number;
  isResubmitted?: boolean;
  region: string;
  deadlineAt: string | null;
  previousRejectionReason: string | null;
  claimedByAgentId: string | null;
  claimedByAgentName: string | null;
  claimedAt: string | null;
  claimedByCurrentAgent: boolean;
}

/** Requête de validation d'une étape */
export interface AgentValidateRequest {
  notes?: string;
  checklist: Record<string, boolean>;
  stepResult: Record<string, string>;
}

export type DocumentReviewStatus = 'A_VERIFIER' | 'CONFORME' | 'NON_CONFORME';

export interface AgentDocumentReviewRequest {
  status: Exclude<DocumentReviewStatus, 'A_VERIFIER'>;
  comment?: string;
}

/** Requête de rejet d'une étape (motif obligatoire, 10-500 caractères) */
export interface AgentRejectRequest {
  motifRejet: string;
}

/** Entrée dans l'historique des dossiers traités par l'agent */
export interface AgentHistoryItem {
  dossierId: string;
  dossierReference: string;
  nomEntreprise: string;
  etapeCode: string;
  etapeLibelle: string;
  decisionPrise: StepStatus;
  motifRejet?: string;
  decidedAt: string;
  startedAt?: string | null;
  indicativeDurationDays?: number;
  entrepreneurNom?: string | null;
}

export interface AuditEvent {
  id: string;
  occurredAt: string;
  action: string;
  actorType: ActorType;
  actorNom: string | null;
  stepTrackingId: string | null;
  reason: string | null;
  details: string | null;
}

export interface PagedAuditEvents {
  items: AuditEvent[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

/** Étape du workflow dans le détail d'un dossier */
export interface StepTracking {
  id: string;
  code: string;
  label: string;
  order: number;
  status: StepStatus;
  actionOwner: ActorType;
  organizationCode: string | null;
  organizationName: string | null;
  indicativeDurationDays: number;
  fraisMad: number;
  paymentRequired: boolean;
  startedAt: string | null;
  decidedAt: string | null;
  rejectionReason: string | null;
  decidedBy: string | null;
  checklistReview: Record<string, boolean>;
  validationResult: Record<string, string>;
}

/** Référence à l'étape courante */
export interface StepReference {
  code: string;
  label: string;
  order: number;
}

/** Détail complet d'un dossier (compatible backend DTO et frontend) */
export interface DossierDetail {
  id: string;
  reference: string;
  nomEntreprise?: string;
  proposedCompanyName?: string;
  formeJuridique?: LegalFormCode;
  legalFormCode?: LegalFormCode;
  statut?: DossierStatus;
  status?: DossierStatus;
  region: string;
  currentStep: StepReference | null;
  capitalMad?: number;
  capitalAmountMad?: number;
  progressionPercentage: number;
  rejectionReason: string | null;
  decidedBy: string | null;
  checklistReview: Record<string, boolean>;
  validationResult: Record<string, string>;
  createdAt: string;
  updatedAt: string;
  descriptionActivite?: string | null;
  activityDescription?: string | null;
  capitalBlockageRequired: boolean;
  adresseSiege?: string | null;
  headOfficeAddress?: string | null;
  submittedAt: string | null;
  finalizedAt: string | null;
  checklistCompletionRate: number;
  workflowVersionId: string;
  steps: StepTracking[];
  // Informations entrepreneur
  entrepreneurNom?: string | null;
  entrepreneurEmail?: string | null;
  entrepreneurCin?: string | null;
  entrepreneurTelephone?: string | null;
}

/** Document déposé par l'entrepreneur */
export interface DocumentResponse {
  id: string;
  documentTypeCode: string;
  documentTypeLabel: string;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  source: 'UPLOADED' | 'GENERATED';
  uploadedAt: string;
  uploadedByUserId: string;
  note: string | null;
  contentUrl: string;
  reviewStatus: DocumentReviewStatus;
  reviewComment: string | null;
  reviewedByAgentName: string | null;
  reviewedAt: string | null;
}
