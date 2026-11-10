import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { DossierService } from '../../services/dossier.service';
import { DocumentService } from '../../services/document.service';
import { PaymentService } from '../../services/payment.service';
import { NotificationService } from '../../services/notification.service';
import {
  AuditEvent, ChecklistItem, DossierDetail, DossierPayload, DossierStatus, DossierSummary,
  LegalFormCode, NotificationItem, Payment, PaymentConfirmation, StepTracking
} from '../../models/entrepreneur.models';

type DashboardView = 'list' | 'detail';
type WizardMode = 'create' | 'edit';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly dossierService = inject(DossierService);
  private readonly documentService = inject(DocumentService);
  private readonly paymentService = inject(PaymentService);
  private readonly notificationService = inject(NotificationService);

  readonly user = this.authService.currentUser;
  readonly legalForms: { code: LegalFormCode; title: string; summary: string }[] = [
    { code: 'AUTO_ENTREPRENEUR', title: 'Auto-entrepreneur', summary: 'Démarrez seul avec un parcours administratif allégé.' },
    { code: 'SARL_AU', title: 'SARL à associé unique', summary: 'Une société structurée pour un seul associé.' },
    { code: 'SARL', title: 'SARL', summary: 'Un cadre souple pour un projet à plusieurs associés.' },
    { code: 'SA', title: 'Société anonyme', summary: 'Pour les projets à capital et gouvernance étendus.' }
  ];
  readonly workflowPreviews: Record<LegalFormCode, { title: string; rule: string; steps: string[] }> = {
    AUTO_ENTREPRENEUR: {
      title: 'Parcours simplifié · 4 étapes',
      rule: 'Sans certificat négatif, statuts, capital ni immatriculation au Registre de Commerce.',
      steps: ['Éligibilité au statut', 'Dépôt du formulaire RNAE', 'Contrôle de conformité', 'Attribution de la carte et de l’identifiant']
    },
    SARL_AU: {
      title: 'Parcours société · 8 étapes',
      rule: 'Le blocage du capital est requis uniquement au-delà de 100 000 DH.',
      steps: ['Certificat négatif OMPIC', 'Statuts signés', 'Blocage du capital si requis', 'Dépôt et contrôle CRI', 'Registre de Commerce', 'Identifiant fiscal', 'Affiliation CNSS', 'Publication légale']
    },
    SARL: {
      title: 'Parcours société · 8 étapes',
      rule: 'Le blocage du capital est requis uniquement au-delà de 100 000 DH.',
      steps: ['Certificat négatif OMPIC', 'Statuts signés', 'Blocage du capital si requis', 'Dépôt et contrôle CRI', 'Registre de Commerce', 'Identifiant fiscal', 'Affiliation CNSS', 'Publication légale']
    },
    SA: {
      title: 'Parcours société · 8 étapes',
      rule: 'Le blocage du capital est toujours requis pour une SA.',
      steps: ['Certificat négatif OMPIC', 'Statuts signés', 'Blocage du capital', 'Dépôt et contrôle CRI', 'Registre de Commerce', 'Identifiant fiscal', 'Affiliation CNSS', 'Publication légale']
    }
  };
  readonly regions = [
    'Tanger-Tétouan-Al Hoceïma', 'Casablanca-Settat', 'Rabat-Salé-Kénitra', 'Marrakech-Safi',
    'Fès-Meknès', 'Souss-Massa', 'Oriental', 'Béni Mellal-Khénifra', 'Drâa-Tafilalet',
    'Guelmim-Oued Noun', 'Laâyoune-Sakia El Hamra', 'Dakhla-Oued Ed-Dahab'
  ];

  view: DashboardView = 'list';
  dossiers: DossierSummary[] = [];
  dossierStatusFilter: DossierStatus | 'ALL' = 'ALL';
  dossierPage = 0;
  dossierTotalPages = 1;
  dossierTotalElements = 0;
  readonly dossierPageSize = 8;
  isLoadingDossiers = false;

  selectedDossier: DossierDetail | null = null;
  checklist: ChecklistItem[] = [];
  auditEvents: AuditEvent[] = [];
  payments: Payment[] = [];
  isLoadingDetail = false;
  isLoadingChecklist = false;
  isLoadingAudit = false;

  isWizardOpen = false;
  wizardMode: WizardMode = 'create';
  wizardStep = 1;
  isSavingDossier = false;
  formError = '';
  editingDossierId: string | null = null;
  dossierForm: DossierPayload = this.emptyDossierForm();

  uploadTarget: ChecklistItem | null = null;
  uploadFile: File | null = null;
  uploadNote = '';
  uploadError = '';
  isUploading = false;
  documentToDelete: ChecklistItem | null = null;
  isDeletingDocument = false;

  isPaymentModalOpen = false;
  paymentToConfirm: Payment | null = null;
  paymentStep: StepTracking | null = null;
  paymentForm: PaymentConfirmation = this.emptyPaymentForm();
  paymentError = '';
  isStartingPayment = false;
  isConfirmingPayment = false;

  isNotificationsOpen = false;
  notifications: NotificationItem[] = [];
  unreadCount = 0;
  isLoadingNotifications = false;

  isLoggingOut = false;
  isSubmitting = false;
  toastMessage = '';
  private toastTimer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.loadDossiers();
    this.loadNotifications();
  }

  get userInitials(): string {
    const first = this.user()?.firstName?.charAt(0) ?? 'E';
    const last = this.user()?.lastName?.charAt(0) ?? 'N';
    return `${first}${last}`.toUpperCase();
  }

  get editableDossier(): boolean {
    return this.selectedDossier?.status === 'BROUILLON' || this.selectedDossier?.status === 'A_CORRIGER';
  }

  get requiredDocumentsComplete(): boolean {
    return this.checklist.filter(item => item.required).every(item => item.submitted);
  }

  get pendingDocumentsCount(): number {
    return this.checklist.filter(item => item.required && !item.submitted).length;
  }

  get isCompanyDossier(): boolean {
    return this.selectedDossier?.legalFormCode !== 'AUTO_ENTREPRENEUR';
  }

  get canGenerateStatuts(): boolean {
    return this.editableDossier && this.isCompanyDossier && !this.isDocumentSubmitted('STATUTS_SIGNES');
  }

  get canGenerateFormulaireCri(): boolean {
    return this.editableDossier && this.isCompanyDossier && !this.isDocumentSubmitted('FORMULAIRE_UNIQUE');
  }

  get paymentRequiredBeforeSubmission(): StepTracking | null {
    if (!this.selectedDossier || this.selectedDossier.status !== 'BROUILLON') return null;
    return this.selectedDossier.steps.find(step => step.status === 'EN_COURS' && step.paymentRequired && !this.hasConfirmedPayment(step.id)) ?? null;
  }

  get shownActionableCount(): number {
    return this.dossiers.filter(dossier => dossier.status === 'BROUILLON' || dossier.status === 'A_CORRIGER').length;
  }

  get shownFinalizedCount(): number {
    return this.dossiers.filter(dossier => dossier.status === 'FINALISE').length;
  }

  get draftLabel(): string {
    return this.wizardMode === 'create' ? 'Nouveau dossier' : `Modifier ${this.selectedDossier?.reference ?? 'le dossier'}`;
  }

  get canChangeLegalForm(): boolean {
    return this.wizardMode === 'create' || this.selectedDossier?.status === 'BROUILLON';
  }

  get isAutoEntrepreneur(): boolean {
    return this.dossierForm.legalFormCode === 'AUTO_ENTREPRENEUR';
  }

  get selectedWorkflowPreview(): { title: string; rule: string; steps: string[] } {
    return this.workflowPreviews[this.dossierForm.legalFormCode];
  }

  get capitalRuleHint(): string {
    if (this.dossierForm.legalFormCode === 'AUTO_ENTREPRENEUR') {
      return 'Aucun capital social ni attestation bancaire ne sont requis pour ce statut.';
    }
    if (this.dossierForm.legalFormCode === 'SA') {
      return 'Le blocage du capital est obligatoire pour une SA. Un minimum indicatif de 300 000 DH est recommandé par la fiche de la plateforme.';
    }
    return 'Le blocage du capital est requis uniquement si le capital dépasse 100 000 DH.';
  }

  loadDossiers(): void {
    this.isLoadingDossiers = true;
    this.dossierService.getMyDossiers(this.dossierStatusFilter, this.dossierPage, this.dossierPageSize).subscribe({
      next: (response) => {
        this.dossiers = response.items ?? [];
        this.dossierTotalElements = response.totalElements ?? 0;
        this.dossierTotalPages = Math.max(response.totalPages ?? 1, 1);
        this.isLoadingDossiers = false;
      },
      error: (error) => {
        this.dossiers = [];
        this.isLoadingDossiers = false;
        this.showToast(this.errorMessage(error, 'Impossible de charger vos dossiers.'));
      }
    });
  }

  changeFilter(): void {
    this.dossierPage = 0;
    this.loadDossiers();
  }

  changePage(delta: number): void {
    const page = this.dossierPage + delta;
    if (page < 0 || page >= this.dossierTotalPages) return;
    this.dossierPage = page;
    this.loadDossiers();
  }

  openDossier(dossierId: string): void {
    this.isLoadingDetail = true;
    this.view = 'detail';
    this.dossierService.getDossier(dossierId).subscribe({
      next: (dossier) => {
        this.selectedDossier = dossier;
        this.isLoadingDetail = false;
        this.loadDossierExtras(dossier.id);
      },
      error: (error) => {
        this.isLoadingDetail = false;
        this.view = 'list';
        this.showToast(this.errorMessage(error, 'Impossible d’ouvrir ce dossier.'));
      }
    });
  }

  backToList(): void {
    this.view = 'list';
    this.selectedDossier = null;
    this.checklist = [];
    this.auditEvents = [];
    this.payments = [];
    this.loadDossiers();
  }

  private loadDossierExtras(dossierId: string): void {
    this.isLoadingChecklist = true;
    this.dossierService.getChecklist(dossierId).subscribe({
      next: (items) => { this.checklist = items ?? []; this.isLoadingChecklist = false; },
      error: () => { this.checklist = []; this.isLoadingChecklist = false; this.showToast('La checklist documentaire n’a pas pu être chargée.'); }
    });
    this.isLoadingAudit = true;
    this.dossierService.getAuditEvents(dossierId).subscribe({
      next: (response) => { this.auditEvents = response.items ?? []; this.isLoadingAudit = false; },
      error: () => { this.auditEvents = []; this.isLoadingAudit = false; }
    });
    this.paymentService.getPayments(dossierId).subscribe({
      next: (payments) => this.payments = payments ?? [],
      error: () => this.payments = []
    });
  }

  refreshSelectedDossier(): void {
    if (!this.selectedDossier) return;
    this.openDossier(this.selectedDossier.id);
    this.loadDossiers();
  }

  openCreateWizard(): void {
    this.wizardMode = 'create';
    this.editingDossierId = null;
    this.dossierForm = this.emptyDossierForm();
    this.wizardStep = 1;
    this.formError = '';
    this.isWizardOpen = true;
  }

  openEditWizard(): void {
    if (!this.selectedDossier || !this.editableDossier) return;
    const dossier = this.selectedDossier;
    this.wizardMode = 'edit';
    this.editingDossierId = dossier.id;
    this.dossierForm = {
      legalFormCode: dossier.legalFormCode,
      proposedCompanyName: dossier.proposedCompanyName ?? '',
      region: dossier.region ?? '',
      activityDescription: dossier.activityDescription ?? '',
      capitalAmountMad: dossier.capitalAmountMad ?? 0,
      headOfficeAddress: dossier.headOfficeAddress ?? ''
    };
    // Démarrer sur les informations préremplies ; la forme reste accessible
    // avec « Retour » pour un dossier encore à l'état Brouillon.
    this.wizardStep = 2;
    this.formError = '';
    this.isWizardOpen = true;
  }

  closeWizard(): void {
    if (!this.isSavingDossier) { this.isWizardOpen = false; this.formError = ''; }
  }

  nextWizardStep(): void {
    this.formError = '';
    if (this.wizardStep === 1 && !this.dossierForm.legalFormCode) {
      this.formError = 'Sélectionnez une forme juridique pour continuer.';
      return;
    }
    if (this.wizardStep === 2) {
      if (!this.dossierForm.proposedCompanyName.trim()) {
        this.formError = 'Indiquez le nom envisagé pour votre entreprise.';
        return;
      }
      if (!this.dossierForm.region.trim()) {
        this.formError = 'La région d’implantation est obligatoire.';
        return;
      }
      if (this.dossierForm.capitalAmountMad === null || this.dossierForm.capitalAmountMad < 0) {
        this.formError = 'Le capital doit être égal ou supérieur à zéro.';
        return;
      }
    }
    this.wizardStep = Math.min(3, this.wizardStep + 1);
  }

  previousWizardStep(): void { this.formError = ''; this.wizardStep = Math.max(1, this.wizardStep - 1); }

  saveDossier(): void {
    if (!this.dossierForm.region.trim() || !this.dossierForm.proposedCompanyName.trim()) {
      this.formError = 'Veuillez compléter les informations obligatoires.';
      this.wizardStep = 2;
      return;
    }
    this.isSavingDossier = true;
    this.formError = '';
    const payload: DossierPayload = {
      ...this.dossierForm,
      proposedCompanyName: this.dossierForm.proposedCompanyName.trim(),
      region: this.dossierForm.region.trim(),
      activityDescription: this.dossierForm.activityDescription.trim(),
      headOfficeAddress: this.dossierForm.headOfficeAddress.trim(),
      capitalAmountMad: this.dossierForm.legalFormCode === 'AUTO_ENTREPRENEUR'
        ? 0
        : Number(this.dossierForm.capitalAmountMad) || 0
    };
    const request = this.wizardMode === 'create'
      ? this.dossierService.createDossier(payload)
      : this.dossierService.updateDossier(this.editingDossierId!, payload);
    request.subscribe({
      next: (dossier) => {
        this.isSavingDossier = false;
        this.isWizardOpen = false;
        this.showToast(this.wizardMode === 'create' ? 'Dossier créé : ajoutez maintenant les pièces demandées.' : 'Informations du dossier mises à jour.');
        this.loadDossiers();
        this.openDossier(dossier.id);
      },
      error: (error) => { this.isSavingDossier = false; this.formError = this.errorMessage(error, 'Le dossier n’a pas pu être enregistré.'); }
    });
  }

  chooseLegalForm(code: LegalFormCode): void { if (this.canChangeLegalForm) this.dossierForm.legalFormCode = code; }

  onFileSelected(item: ChecklistItem, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.selectUploadFile(item, file);
    input.value = '';
  }

  onFileDropped(item: ChecklistItem, event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0];
    if (file) this.selectUploadFile(item, file);
  }

  private selectUploadFile(item: ChecklistItem, file: File): void {
    const validExtension = /\.(pdf|jpe?g|png)$/i.test(file.name);
    if (!validExtension || file.size > 5 * 1024 * 1024) {
      this.uploadTarget = null;
      this.uploadFile = null;
      this.uploadError = !validExtension ? 'Seuls les fichiers PDF, JPG, JPEG ou PNG sont acceptés.' : 'La taille du fichier ne doit pas dépasser 5 Mo.';
      this.showToast(this.uploadError);
      return;
    }
    this.uploadTarget = item;
    this.uploadFile = file;
    this.uploadNote = '';
    this.uploadError = '';
  }

  cancelUpload(): void {
    if (this.isUploading) return;
    this.uploadTarget = null;
    this.uploadFile = null;
    this.uploadNote = '';
    this.uploadError = '';
  }

  uploadDocument(): void {
    if (!this.selectedDossier || !this.uploadTarget || !this.uploadFile) return;
    this.isUploading = true;
    this.uploadError = '';
    this.documentService.upload(this.selectedDossier.id, this.uploadTarget.documentTypeCode, this.uploadFile, this.uploadNote).subscribe({
      next: () => {
        this.isUploading = false;
        this.showToast('Document ajouté avec succès.');
        this.cancelUpload();
        this.refreshSelectedDossier();
      },
      error: (error) => { this.isUploading = false; this.uploadError = this.errorMessage(error, 'Le document n’a pas pu être déposé.'); }
    });
  }

  requestDocumentDeletion(item: ChecklistItem): void {
    if (!this.selectedDossier || !item.activeDocumentId) return;
    this.documentToDelete = item;
  }

  cancelDocumentDeletion(): void {
    if (!this.isDeletingDocument) this.documentToDelete = null;
  }

  confirmDocumentDeletion(): void {
    if (!this.selectedDossier || !this.documentToDelete?.activeDocumentId) return;
    this.isDeletingDocument = true;
    this.documentService.delete(this.selectedDossier.id, this.documentToDelete.activeDocumentId).subscribe({
      next: () => {
        this.isDeletingDocument = false;
        this.documentToDelete = null;
        this.showToast('Document supprimé.');
        this.refreshSelectedDossier();
      },
      error: (error) => {
        this.isDeletingDocument = false;
        this.showToast(this.errorMessage(error, 'La suppression a échoué.'));
      }
    });
  }

  downloadDocument(documentId: string, filename: string): void {
    if (!this.selectedDossier) return;
    this.documentService.download(this.selectedDossier.id, documentId).subscribe({
      next: (blob) => this.downloadBlob(blob, filename),
      error: (error) => this.showToast(this.errorMessage(error, 'Le téléchargement a échoué.'))
    });
  }

  generateDocument(type: 'STATUTS' | 'FORMULAIRE_UNIQUE'): void {
    if (!this.selectedDossier) return;
    this.documentService.generate(this.selectedDossier.id, type).subscribe({
      next: (document) => {
        this.showToast('Modèle pré-rempli téléchargé : vérifiez-le, signez-le puis déposez la version finale.');
        this.refreshSelectedDossier();
        this.downloadDocument(document.id, document.fileName);
      },
      error: (error) => this.showToast(this.errorMessage(error, 'La génération du document a échoué.'))
    });
  }

  submitDossier(): void {
    if (!this.selectedDossier || !this.editableDossier || this.isSubmitting) return;
    if (!this.requiredDocumentsComplete) {
      this.showToast(`${this.pendingDocumentsCount} pièce(s) obligatoire(s) restent à déposer avant la soumission.`);
      return;
    }
    if (this.paymentRequiredBeforeSubmission) {
      this.showToast('Le règlement des frais de cette première étape est requis avant la soumission.');
      this.startPayment(this.paymentRequiredBeforeSubmission);
      return;
    }
    this.isSubmitting = true;
    this.dossierService.submitDossier(this.selectedDossier.id).subscribe({
      next: (result) => {
        this.isSubmitting = false;
        this.showToast(result.message || 'Dossier soumis avec succès.');
        this.refreshSelectedDossier();
        this.loadNotifications();
      },
      error: (error) => { this.isSubmitting = false; this.showToast(this.errorMessage(error, 'La soumission a échoué.')); }
    });
  }

  startPayment(step: StepTracking): void {
    if (!this.selectedDossier || this.isStartingPayment) return;
    const pending = this.payments.find(payment => payment.stepTrackingId === step.id && payment.status === 'EN_ATTENTE');
    if (pending) { this.openPaymentModal(pending, step); return; }
    this.isStartingPayment = true;
    this.paymentService.initiate(this.selectedDossier.id, step.id, step.feeMad).subscribe({
      next: (payment) => {
        this.isStartingPayment = false;
        this.payments = [payment, ...this.payments.filter(item => item.id !== payment.id)];
        this.openPaymentModal(payment, step);
      },
      error: (error) => { this.isStartingPayment = false; this.showToast(this.errorMessage(error, 'Impossible d’initialiser le paiement.')); }
    });
  }

  private openPaymentModal(payment: Payment, step: StepTracking): void {
    this.paymentToConfirm = payment;
    this.paymentStep = step;
    this.paymentForm = this.emptyPaymentForm();
    this.paymentError = '';
    this.isPaymentModalOpen = true;
  }

  closePaymentModal(): void {
    if (this.isConfirmingPayment) return;
    this.isPaymentModalOpen = false;
    this.paymentToConfirm = null;
    this.paymentStep = null;
    this.paymentError = '';
  }

  confirmPayment(): void {
    if (!this.selectedDossier || !this.paymentToConfirm) return;
    const payment = this.paymentForm;
    if (!payment.cardNumber.replace(/\s/g, '') || !payment.cardHolder.trim() || !/^(0[1-9]|1[0-2])\/\d{2}$/.test(payment.expirationDate) || !/^\d{3,4}$/.test(payment.cvv)) {
      this.paymentError = 'Veuillez renseigner une carte de démonstration valide (MM/AA et CVV à 3 ou 4 chiffres).';
      return;
    }
    this.isConfirmingPayment = true;
    this.paymentError = '';
    this.paymentService.confirm(this.selectedDossier.id, this.paymentToConfirm.id, {
      ...payment,
      cardNumber: payment.cardNumber.replace(/\s/g, ''),
      cardHolder: payment.cardHolder.trim()
    }).subscribe({
      next: (confirmed) => {
        this.isConfirmingPayment = false;
        this.payments = this.payments.map(item => item.id === confirmed.id ? confirmed : item);
        this.closePaymentModal();
        this.showToast('Paiement simulé confirmé. Votre dossier a été actualisé.');
        this.refreshSelectedDossier();
        this.loadNotifications();
      },
      error: (error) => { this.isConfirmingPayment = false; this.paymentError = this.errorMessage(error, 'La confirmation du paiement a échoué.'); }
    });
  }

  hasConfirmedPayment(stepId: string): boolean { return this.payments.some(payment => payment.stepTrackingId === stepId && payment.status === 'CONFIRME'); }

  downloadReceipt(payment: Payment): void {
    if (!this.selectedDossier) return;
    this.paymentService.downloadReceipt(this.selectedDossier.id, payment.id).subscribe({
      next: (blob) => this.downloadBlob(blob, `Recu_${payment.simulatedReference}.pdf`),
      error: (error) => this.showToast(this.errorMessage(error, 'Le reçu n’a pas pu être téléchargé.'))
    });
  }

  toggleNotifications(): void { this.isNotificationsOpen = !this.isNotificationsOpen; if (this.isNotificationsOpen) this.loadNotifications(); }

  loadNotifications(): void {
    this.isLoadingNotifications = true;
    this.notificationService.getNotifications(undefined, 0, 15).subscribe({
      next: (response) => { this.notifications = response.items ?? []; this.unreadCount = response.unreadCount ?? 0; this.isLoadingNotifications = false; },
      error: () => { this.isLoadingNotifications = false; }
    });
  }

  openNotification(notification: NotificationItem): void {
    const openRelatedDossier = () => { this.isNotificationsOpen = false; if (notification.dossierId) this.openDossier(notification.dossierId); };
    if (notification.read) { openRelatedDossier(); return; }
    this.notificationService.markAsRead(notification.id).subscribe({
      next: () => { notification.read = true; this.unreadCount = Math.max(0, this.unreadCount - 1); openRelatedDossier(); },
      error: () => openRelatedDossier()
    });
  }

  markAllNotificationsRead(): void {
    this.notificationService.markAllAsRead().subscribe({
      next: () => { this.notifications = this.notifications.map(notification => ({ ...notification, read: true })); this.unreadCount = 0; },
      error: () => this.showToast('Impossible de mettre à jour les notifications.')
    });
  }

  logout(): void {
    this.isLoggingOut = true;
    this.authService.logout().subscribe({ next: () => this.isLoggingOut = false, error: () => this.isLoggingOut = false });
  }

  statusLabel(status: DossierStatus): string {
    return { BROUILLON: 'Brouillon', SOUMIS: 'Soumis', EN_COURS: 'En cours', A_CORRIGER: 'À corriger', FINALISE: 'Finalisé' }[status];
  }

  stepStatusLabel(status: string): string { return { EN_ATTENTE: 'À venir', EN_COURS: 'En cours', VALIDEE: 'Validée', REJETEE: 'À corriger' }[status] ?? status; }
  legalFormLabel(code: LegalFormCode): string { return this.legalForms.find(form => form.code === code)?.title ?? code.replace('_', ' '); }
  formatMoney(value: number | null | undefined): string { return new Intl.NumberFormat('fr-MA', { style: 'currency', currency: 'MAD', maximumFractionDigits: 2 }).format(value ?? 0); }
  formatDate(value: string | null | undefined): string { return value ? new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'; }

  private emptyDossierForm(): DossierPayload {
    return { legalFormCode: 'SARL_AU', proposedCompanyName: '', region: '', activityDescription: '', capitalAmountMad: 0, headOfficeAddress: '' };
  }

  private isDocumentSubmitted(documentTypeCode: string): boolean {
    return this.checklist.some(item => item.documentTypeCode === documentTypeCode && item.submitted);
  }
  private emptyPaymentForm(): PaymentConfirmation { return { cardNumber: '', cardHolder: '', expirationDate: '', cvv: '' }; }
  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }
  private errorMessage(error: unknown, fallback: string): string {
    const candidate = error as { error?: { message?: unknown }; message?: unknown };
    const message = candidate?.error?.message ?? candidate?.message;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }
  private showToast(message: string): void {
    this.toastMessage = message;
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => this.toastMessage = '', 4200);
  }
}
