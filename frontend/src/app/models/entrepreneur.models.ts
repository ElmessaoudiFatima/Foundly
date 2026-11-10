export type LegalFormCode = 'AUTO_ENTREPRENEUR' | 'SARL' | 'SARL_AU' | 'SA';
export type DossierStatus = 'BROUILLON' | 'SOUMIS' | 'EN_COURS' | 'A_CORRIGER' | 'FINALISE';
export type StepStatus = 'EN_ATTENTE' | 'EN_COURS' | 'VALIDEE' | 'REJETEE';
export type PaymentStatus = 'EN_ATTENTE' | 'CONFIRME' | 'ECHOUE';

export interface StepReference {
  code: string;
  label: string;
  order: number;
}

export interface DossierSummary {
  id: string;
  reference: string;
  proposedCompanyName: string | null;
  legalFormCode: LegalFormCode;
  status: DossierStatus;
  region: string;
  currentStep: StepReference | null;
  capitalAmountMad: number;
  progressionPercentage: number;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface StepTracking {
  id: string;
  code: string;
  label: string;
  order: number;
  status: StepStatus;
  actionOwner: string;
  organizationCode: string | null;
  organizationName: string | null;
  indicativeDurationDays: number | null;
  feeMad: number | null;
  paymentRequired: boolean;
  startedAt: string | null;
  decidedAt: string | null;
  rejectionReason: string | null;
}

export interface DossierDetail extends DossierSummary {
  activityDescription: string | null;
  capitalBlockageRequired: boolean;
  headOfficeAddress: string | null;
  submittedAt: string | null;
  finalizedAt: string | null;
  checklistCompletionRate: number;
  workflowVersionId: string;
  steps: StepTracking[];
}

export interface PagedDossiers {
  items: DossierSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface DossierPayload {
  legalFormCode: LegalFormCode;
  proposedCompanyName: string;
  region: string;
  activityDescription: string;
  capitalAmountMad: number;
  headOfficeAddress: string;
}

export interface ChecklistItem {
  documentTypeCode: string;
  label: string;
  required: boolean;
  acceptedMimeTypes: string[];
  submitted: boolean;
  activeDocumentId: string | null;
  activeDocumentName: string | null;
  correctionRequired: boolean;
  correctionReason: string | null;
}

export interface DocumentItem {
  id: string;
  documentTypeCode: string;
  documentTypeLabel: string;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  source: 'UPLOADED' | 'GENERATED' | string;
  uploadedAt: string;
  uploadedByUserId: string;
  note: string | null;
  contentUrl: string;
}

export interface SubmissionResult {
  dossierId: string;
  status: DossierStatus;
  currentStep: StepReference | null;
  submittedAt: string;
  message: string;
}

export interface Payment {
  id: string;
  dossierId: string;
  stepTrackingId: string | null;
  stepLabel: string;
  amountMad: number;
  status: PaymentStatus;
  simulatedReference: string;
  method: string;
  cardNumberMasked: string | null;
  createdAt: string;
  confirmedAt: string | null;
}

export interface PaymentConfirmation {
  cardNumber: string;
  cardHolder: string;
  expirationDate: string;
  cvv: string;
}

export interface AuditEvent {
  id: string;
  occurredAt: string;
  action: string;
  actorType: string;
  actorNom: string;
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

export interface NotificationItem {
  id: string;
  title: string;
  body: string;
  channel: string;
  type: string;
  dossierId: string | null;
  dossierReference: string | null;
  read: boolean;
  createdAt: string;
  readAt: string | null;
}

export interface PagedNotifications {
  items: NotificationItem[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  unreadCount: number;
}
