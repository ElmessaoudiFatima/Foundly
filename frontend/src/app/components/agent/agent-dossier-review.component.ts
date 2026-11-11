import { CommonModule, DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { forkJoin, map } from 'rxjs';
import { AgentService } from '../../services/agent.service';
import {
  AgentQueueItem,
  AgentValidateRequest,
  AuditEvent,
  DossierDetail,
  DocumentResponse,
  StepTracking
} from '../../models/agent.models';
import {
  AGENT_REVIEW_CONFIG,
  AgentReviewConfig,
  AgentReviewField,
  WORKFLOW_STEP_LABELS
} from './agent-review.config';

@Component({
  selector: 'app-agent-dossier-review',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  templateUrl: './agent-dossier-review.component.html',
  styleUrls: ['./agent-dossier-review.component.css']
})
export class AgentDossierReviewComponent implements OnChanges, OnDestroy {
  private readonly agentService = inject(AgentService);
  private readonly sanitizer = inject(DomSanitizer);

  @Input() queueItem: AgentQueueItem | null = null;
  @Input() fromHistory = false;
  @Output() back = new EventEmitter<void>();
  @Output() completed = new EventEmitter<{ reference: string; decision: 'VALIDEE' | 'REJETEE' }>();
  @Output() queueItemUpdated = new EventEmitter<AgentQueueItem>();

  @Input({ required: true }) dossierId = '';

  dossier: DossierDetail | null = null;
  documents: DocumentResponse[] = [];
  auditEvents: AuditEvent[] = [];
  loading = true;
  loadingError: string | null = null;
  notFound = false;
  busy = false;
  actionError: string | null = null;
  @Input() organizationCode = '';
  checklist: Record<string, boolean> = {};
  resultValues: Record<string, string | boolean> = {};
  rejectionReason = '';
  rejectionError: string | null = null;
  rejectConfirmStep = 0;
  showRejectModal = false;
  showValidateModal = false;
  showPreviousRejection = false;
  showCompanyDetails = false;
  previewDocument: DocumentResponse | null = null;
  previewUrl: SafeResourceUrl | null = null;
  private previewObjectUrl: string | null = null;
  documentBusyId: string | null = null;
  toastMessage: string | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['dossierId'] || changes['fromHistory']) && this.dossierId) this.load(this.dossierId);
  }

  get config(): AgentReviewConfig | null {
    return AGENT_REVIEW_CONFIG[this.organizationCode] ?? null;
  }

  get currentStep(): StepTracking | null {
    return this.dossier?.steps.find(step => step.status === 'EN_COURS' || step.status === 'REJETEE') ?? null;
  }

  get currentStatusLabel(): string {
    if (this.dossier?.status === 'A_CORRIGER' || this.currentStep?.status === 'REJETEE') return 'À corriger';
    return this.queueItem?.claimedByCurrentAgent ? 'En cours' : 'À traiter';
  }

  get isClaimedByCurrentAgent(): boolean {
    return !!this.queueItem?.claimedByCurrentAgent;
  }

  get isClaimedByOtherAgent(): boolean {
    return !!this.queueItem?.claimedByAgentId && !this.queueItem.claimedByCurrentAgent;
  }

  get isReadOnly(): boolean {
    return this.fromHistory || this.dossier?.status === 'FINALISE' ||
      this.currentStep?.status === 'REJETEE' || this.currentStep?.status === 'VALIDEE';
  }

  get relevantDocuments(): DocumentResponse[] {
    const allowed = this.config?.documentTypes;
    return this.documents.filter(document => !allowed || allowed.includes(document.documentTypeCode));
  }

  get checkedCount(): number {
    return Object.values(this.checklist).filter(Boolean).length;
  }

  get checklistComplete(): boolean {
    return !!this.config && this.config.checklist.every(item => this.checklist[item.key] === true);
  }

  get documentsComplete(): boolean {
    return this.relevantDocuments.length > 0 &&
      this.relevantDocuments.every(document => document.reviewStatus === 'CONFORME');
  }

  get resultComplete(): boolean {
    return !!this.config && this.visibleResultFields
      .filter(field => field.required)
      .every(field => {
        const value = this.resultValues[field.key];
        return field.type === 'checkbox' ? value === true : typeof value === 'string' && value.trim().length > 0;
      });
  }

  get visibleResultFields(): AgentReviewField[] {
    return (this.config?.resultFields ?? []).filter(field =>
      !['capitalAmountConfirmed', 'companyNameConfirmed', 'partnersIdentityConfirmed'].includes(field.key) ||
      this.dossier?.capitalBlockageRequired === true
    );
  }

  get canValidate(): boolean {
    return this.isClaimedByCurrentAgent && !this.isReadOnly &&
      this.checklistComplete && this.documentsComplete && this.resultComplete;
  }

  get previousRejectionReason(): string | null {
    return this.queueItem?.previousRejectionReason ||
      this.dossier?.steps.find(step => step.rejectionReason)?.rejectionReason || null;
  }

  get isResubmitted(): boolean {
    return !!this.queueItem?.isResubmitted || !!this.previousRejectionReason;
  }

  get workflowSteps(): StepTracking[] {
    return [...(this.dossier?.steps ?? [])].sort((a, b) => a.order - b.order);
  }

  get priorDecisions(): StepTracking[] {
    return this.workflowSteps.filter(step => step.status === 'VALIDEE' || step.status === 'REJETEE');
  }

  get companyFields(): { key: string; label: string; value: string }[] {
    if (!this.dossier || !this.config) return [];
    const dossier = this.dossier;
    const values: Record<string, { label: string; value: string }> = {
      denomination: { label: 'Dénomination envisagée', value: dossier.proposedCompanyName || dossier.nomEntreprise || '—' },
      legalForm: { label: 'Forme juridique', value: dossier.formeJuridique || dossier.legalFormCode || '—' },
      activity: { label: 'Activité', value: dossier.activityDescription || dossier.descriptionActivite || '—' },
      entrepreneur: { label: 'Entrepreneur', value: dossier.entrepreneurNom || '—' },
      cin: { label: 'CIN', value: this.maskCin(dossier.entrepreneurCin) },
      region: { label: 'Région', value: dossier.region || '—' },
      capital: { label: 'Capital social', value: dossier.capitalMad || dossier.capitalAmountMad ? `${(dossier.capitalMad || dossier.capitalAmountMad)!.toLocaleString('fr-FR')} MAD` : '—' },
      contact: { label: 'Contact', value: [dossier.entrepreneurEmail, dossier.entrepreneurTelephone].filter(Boolean).join(' · ') || '—' }
    };
    return this.config.companyFields.map(key => ({ key, ...values[key] })).filter(field => !!field.label);
  }

  get organizationLabel(): string {
    return this.config?.title || this.organizationCode || 'Organisme';
  }

  load(dossierId: string): void {
    this.loading = true;
    this.loadingError = null;
    this.notFound = false;
    this.dossier = null;
    this.documents = [];
    this.checklist = {};
    this.resultValues = {};

    if (!this.fromHistory) {
      this.agentService.getQueue().subscribe({
        next: items => {
          const freshItem = items.find(item => item.dossierId === dossierId) || null;
          if (freshItem) {
            this.queueItem = freshItem;
            this.queueItemUpdated.emit(freshItem);
          } else {
            this.queueItem = null;
          }
        },
        error: error => this.actionError = error.error?.message || 'Impossible d’actualiser l’état de la prise en charge.'
      });
    } else {
      this.queueItem = null;
    }

    const dossierRequest = this.fromHistory
      ? this.agentService.getHistoricalDossier(dossierId)
      : this.agentService.getDossierDetail(dossierId);
    const documentsRequest = this.fromHistory
      ? this.agentService.getHistoricalDocuments(dossierId)
      : this.agentService.getDocuments(dossierId);
    const requests = this.fromHistory
      ? forkJoin({
          dossier: dossierRequest,
          documents: documentsRequest,
          audit: this.agentService.getHistoricalAuditEvents(dossierId)
        }).pipe(map(({ dossier, documents, audit }) => ({ dossier, documents, auditEvents: audit.items })))
      : forkJoin({ dossier: dossierRequest, documents: documentsRequest })
          .pipe(map(({ dossier, documents }) => ({ dossier, documents, auditEvents: [] as AuditEvent[] })));

    requests.subscribe({
      next: ({ dossier, documents, auditEvents }) => {
        this.dossier = dossier;
        this.documents = documents;
        this.auditEvents = auditEvents;
        const tracking = this.currentStep;
        if (tracking) {
          this.checklist = { ...tracking.checklistReview };
          this.resultValues = Object.fromEntries(
            Object.entries(tracking.validationResult).map(([key, value]) => [key, value === 'true' ? true : value])
          );
        }
        this.loading = false;
      },
      error: error => {
        this.notFound = error.status === 404;
        this.loadingError = this.notFound
          ? 'Ce dossier est introuvable ou n’est plus accessible.'
          : error.status === 403
            ? 'L’étape actuelle ne relève pas de votre organisme (RG5).'
            : 'Impossible de charger le dossier et ses documents. Réessayez.';
        this.loading = false;
      }
    });
  }

  takeInCharge(): void {
    if (!this.dossier || this.busy) return;
    this.busy = true;
    this.agentService.takeInCharge(this.dossier.id).subscribe({
      next: item => {
        this.queueItem = item;
        this.queueItemUpdated.emit(item);
        this.busy = false;
        this.notify(`Dossier ${item.dossierReference} pris en charge.`);
      },
      error: error => {
        this.busy = false;
        this.actionError = error.error?.message || 'Impossible de prendre ce dossier en charge.';
        if (error.status === 409) {
          this.agentService.getQueue().subscribe({
            next: items => {
              this.queueItem = items.find(item => item.dossierId === this.dossier?.id) || null;
              if (this.queueItem) this.queueItemUpdated.emit(this.queueItem);
            },
            error: refreshError => this.actionError = refreshError.error?.message || 'Impossible d’actualiser l’état du dossier.'
          });
        }
      }
    });
  }

  reviewDocument(document: DocumentResponse, status: 'CONFORME' | 'NON_CONFORME', comment = ''): void {
    if (!this.dossier || !this.isClaimedByCurrentAgent || this.isReadOnly) return;
    const cleanComment = comment.trim();
    if (status === 'NON_CONFORME' && !cleanComment) {
      this.actionError = 'Un commentaire est obligatoire pour déclarer une pièce non conforme.';
      return;
    }
    this.documentBusyId = document.id;
    this.actionError = null;
    this.agentService.reviewDocument(this.dossier.id, document.id, {
      status,
      ...(status === 'NON_CONFORME' ? { comment: cleanComment } : {})
    }).subscribe({
      next: () => {
        this.documents = this.documents.map(item => item.id === document.id
          ? { ...item, reviewStatus: status, reviewComment: status === 'NON_CONFORME' ? cleanComment : null }
          : item);
        this.documentBusyId = null;
      },
      error: error => {
        this.documentBusyId = null;
        this.actionError = error.error?.message || 'Impossible d’enregistrer le contrôle de cette pièce.';
      }
    });
  }

  async markDocumentNonCompliant(document: DocumentResponse): Promise<void> {
    const comment = window.prompt('Motif de non-conformité obligatoire :', document.reviewComment || '');
    if (comment === null) return;
    this.reviewDocument(document, 'NON_CONFORME', comment);
  }

  preview(document: DocumentResponse): void {
    if (!this.dossier) return;
    this.documentBusyId = document.id;
    const request = this.fromHistory
      ? this.agentService.downloadHistoricalDocument(this.dossier.id, document.id)
      : this.agentService.downloadDocument(this.dossier.id, document.id);
    request.subscribe({
      next: blob => {
        this.clearPreview();
        this.previewObjectUrl = URL.createObjectURL(blob);
        this.previewUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.previewObjectUrl);
        this.previewDocument = document;
        this.documentBusyId = null;
      },
      error: error => {
        this.documentBusyId = null;
        this.actionError = error.error?.message || 'Impossible de prévisualiser ce document.';
      }
    });
  }

  download(document: DocumentResponse): void {
    if (!this.dossier) return;
    this.documentBusyId = document.id;
    const request = this.fromHistory
      ? this.agentService.downloadHistoricalDocument(this.dossier.id, document.id)
      : this.agentService.downloadDocument(this.dossier.id, document.id);
    request.subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const anchor = window.document.createElement('a');
        anchor.href = url;
        anchor.download = document.fileName;
        anchor.click();
        URL.revokeObjectURL(url);
        this.documentBusyId = null;
      },
      error: error => {
        this.documentBusyId = null;
        this.actionError = error.error?.message || 'Impossible de télécharger ce document.';
      }
    });
  }

  uploadResultFile(field: AgentReviewField, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.dossier) return;
    this.busy = true;
    this.agentService.uploadDocument(this.dossier.id, file, field.key === 'publicationCertificate'
      ? 'ATTESTATION_PARUTION'
      : 'AUTRE').subscribe({
      next: document => {
        this.documents = [...this.documents, document];
        this.resultValues[field.key] = document.fileName;
        this.busy = false;
      },
      error: error => {
        this.busy = false;
        this.actionError = error.error?.message || 'Impossible de téléverser le document.';
      }
    });
  }

  openValidate(): void {
    if (this.canValidate) this.showValidateModal = true;
  }

  validate(): void {
    if (!this.dossier || !this.canValidate || this.busy) return;
    this.busy = true;
    this.actionError = null;
    const stepResult: Record<string, string> = Object.fromEntries(
      Object.entries(this.resultValues).map(([key, value]) => [key, String(value)])
    );
    const request: AgentValidateRequest = { checklist: this.checklist, stepResult };
    this.agentService.validateStep(this.dossier.id, request).subscribe({
      next: () => this.finish('VALIDEE'),
      error: error => {
        this.busy = false;
        this.showValidateModal = false;
        this.actionError = error.error?.message || 'La validation a échoué.';
      }
    });
  }

  confirmReject(): void {
    if (!this.dossier || !this.isClaimedByCurrentAgent || this.isReadOnly || this.busy) return;
    const reason = this.rejectionReason.trim();
    if (this.rejectConfirmStep === 0) {
      if (reason.length < 10 || reason.length > 500) {
        this.rejectionError = 'Le motif doit contenir entre 10 et 500 caractères (RG8).';
        return;
      }
      this.rejectionError = null;
      this.rejectConfirmStep = 1;
      return;
    }
    this.busy = true;
    this.agentService.rejectStep(this.dossier.id, { motifRejet: reason }).subscribe({
      next: () => this.finish('REJETEE'),
      error: error => {
        this.busy = false;
        this.showRejectModal = false;
        this.actionError = error.error?.message || 'Le rejet a échoué.';
      }
    });
  }

  closeReject(): void {
    this.showRejectModal = false;
    this.rejectConfirmStep = 0;
    this.rejectionReason = '';
    this.rejectionError = null;
  }

  closePreview(): void {
    this.previewDocument = null;
    this.clearPreview();
  }

  ngOnDestroy(): void {
    this.clearPreview();
  }

  getWaitLabel(startedAt: string | null): string {
    if (!startedAt) return '—';
    const hours = Math.max(0, Math.floor((Date.now() - new Date(startedAt).getTime()) / 3600000));
    return `${Math.floor(hours / 24)} j ${hours % 24} h`;
  }

  isOverdue(): boolean {
    return !!this.queueItem?.deadlineAt && new Date(this.queueItem.deadlineAt).getTime() < Date.now();
  }

  stepLabel(step: StepTracking): string {
    return WORKFLOW_STEP_LABELS[step.code] || step.label || step.code;
  }

  statusLabel(status: string): string {
    if (status === 'VALIDEE') return 'Validée';
    if (status === 'REJETEE') return 'Rejetée';
    if (status === 'EN_COURS') return 'En cours';
    return 'À venir';
  }

  auditActionLabel(action: string): string {
    return action.toLowerCase().replaceAll('_', ' ');
  }

  maskCin(cin: string | null | undefined): string {
    if (!cin) return '—';
    if (cin.length <= 4) return '••••';
    return `${cin.slice(0, 2)}${'•'.repeat(Math.max(4, cin.length - 4))}${cin.slice(-2)}`;
  }

  onChecklistChange(key: string, event: Event): void {
    this.checklist[key] = (event.target as HTMLInputElement).checked;
  }

  onResultCheckboxChange(key: string, event: Event): void {
    this.resultValues[key] = (event.target as HTMLInputElement).checked;
  }

  private finish(decision: 'VALIDEE' | 'REJETEE'): void {
    const reference = this.dossier?.reference || '';
    this.busy = false;
    this.showValidateModal = false;
    this.closeReject();
    this.notify(decision === 'VALIDEE' ? 'Étape validée et décision journalisée.' : 'Étape rejetée et décision journalisée.');
    this.completed.emit({ reference, decision });
  }

  private notify(message: string): void {
    this.toastMessage = message;
    window.setTimeout(() => this.toastMessage = null, 4000);
  }

  private clearPreview(): void {
    if (this.previewObjectUrl) URL.revokeObjectURL(this.previewObjectUrl);
    this.previewObjectUrl = null;
    this.previewUrl = null;
  }
}
