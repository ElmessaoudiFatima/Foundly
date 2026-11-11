import { Component, inject, OnInit, signal, computed, HostListener } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { AgentService } from '../../services/agent.service';
import {
  AgentQueueItem,
  AgentHistoryItem,
  DossierDetail,
  DocumentResponse,
  StepTracking
} from '../../models/agent.models';
import { AgentDossierReviewComponent } from './agent-dossier-review.component';

export type AgentTab = 'dashboard' | 'queue' | 'detail' | 'history' | 'profile';

/**
 * Espace Agent Administratif — fidèle aux maquettes Figma livrées.
 * Réf. CDC § 8.2, RG5, RG7, RG8, RG15.
 */
@Component({
  selector: 'app-agent',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, DecimalPipe, AgentDossierReviewComponent],
  templateUrl: './agent.component.html',
  styleUrls: ['./agent.component.css']
})
export class AgentComponent implements OnInit {
  private readonly agentService = inject(AgentService);
  readonly authService = inject(AuthService);

  // ─── Navigation ───────────────────────────────────────────
  activeTab = signal<AgentTab>('dashboard');
  returnTab = signal<'queue' | 'history'>('queue');

  // ─── Profil & Menu ────────────────────────────────────────
  isProfileMenuOpen = false;
  isAgentVariantOpen = false;

  readonly user = this.authService.currentUser;

  readonly userInitials = computed(() => {
    const u = this.user();
    if (!u) return 'AG';
    const first = u.firstName ? u.firstName.charAt(0).toUpperCase() : '';
    const last = u.lastName ? u.lastName.charAt(0).toUpperCase() : '';
    return (first + last) || 'AG';
  });

  readonly userName = computed(() => {
    const u = this.user();
    if (!u) return 'Agent';
    return `${u.firstName || ''} ${u.lastName || ''}`.trim();
  });

  readonly currentOrganisme = computed(() => {
    const email = this.user()?.email?.toLowerCase() || '';
    if (email.includes('ompic')) return 'OMPIC';
    if (email.includes('cri')) return 'CRI';
    if (email.includes('tribunal')) return 'TRIBUNAL';
    if (email.includes('dgi')) return 'DGI';
    if (email.includes('cnss')) return 'CNSS';
    if (email.includes('journal')) return 'JOURNAL';
    return 'GUICHET';
  });

  readonly currentOrganismeLabel = computed(() => this.getOrganismeLabel(this.currentOrganisme()));

  readonly currentOrganismeShort = computed(() => {
    const labels: Record<string, string> = {
      'OMPIC': 'Agent OMPIC',
      'CRI': 'Agent CRI',
      'TRIBUNAL': 'Agent Tribunal',
      'DGI': 'Agent DGI',
      'CNSS': 'Agent CNSS',
      'JOURNAL': 'Agent JAL',
      'GUICHET': 'Agent Guichet'
    };
    return labels[this.currentOrganisme()] || 'Agent';
  });

  readonly agentRoleLabel = computed(() => {
    const org = this.currentOrganisme();
    return `Agent ${org}`;
  });

  // ─── Données ──────────────────────────────────────────────
  queue = signal<AgentQueueItem[]>([]);
  history = signal<AgentHistoryItem[]>([]);
  selectedDossier = signal<DossierDetail | null>(null);
  selectedDossierId = signal<string | null>(null);
  selectedDocuments = signal<DocumentResponse[]>([]);
  selectedQueueItem = signal<AgentQueueItem | null>(null);

  // ─── Filtres File d'attente ───────────────────────────────
  searchQuery = signal<string>('');
  filterForme = signal<string>('Toutes');
  filterRegion = signal<string>('Toutes');
  filterDateFrom = signal<string>('');
  filterDateTo = signal<string>('');
  filterSort = signal<string>('Plus ancien');
  filterStatut = signal<string>('Tous');
  isTakingCharge = signal(false);
  isReleasingClaim = signal(false);

  // ─── Filtres Historique ────────────────────────────────────
  historySearch = signal<string>('');
  historyDecisionFilter = signal<string>('Toutes les décisions');
  historyPeriodFilter = signal<string>('30 derniers jours');

  filteredQueue = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    const forme = this.filterForme();
    const region = this.filterRegion();
    const dateFrom = this.filterDateFrom();
    const dateTo = this.filterDateTo();
    const status = this.filterStatut();
    const filtered = this.queue().filter(item => {
      const matchSearch = !q || item.dossierReference.toLowerCase().includes(q);
      const matchForme = forme === 'Toutes' || item.formeJuridique === forme;
      const matchRegion = region === 'Toutes' || item.region === region;
      const arrivalDate = item.etapeStartedAt ? new Date(item.etapeStartedAt).toISOString().slice(0, 10) : '';
      const matchFrom = !dateFrom || (arrivalDate !== '' && arrivalDate >= dateFrom);
      const matchTo = !dateTo || (arrivalDate !== '' && arrivalDate <= dateTo);
      const matchStatus = status === 'Tous' ||
        (status === 'Nouveau' && !item.isResubmitted) ||
        (status === 'Resoumis après correction' && item.isResubmitted);
      return matchSearch && matchForme && matchRegion && matchFrom && matchTo && matchStatus;
    });
    const sort = this.filterSort();
    return filtered.sort((a, b) => {
      if (sort === 'Référence') return a.dossierReference.localeCompare(b.dossierReference);
      const aDate = a.etapeStartedAt ? new Date(a.etapeStartedAt).getTime() : 0;
      const bDate = b.etapeStartedAt ? new Date(b.etapeStartedAt).getTime() : 0;
      return sort === 'Plus récent' ? bDate - aDate : aDate - bDate;
    });
  });

  readonly queueRegions = computed(() =>
    [...new Set(this.queue().map(item => item.region).filter((region): region is string => !!region))]
      .sort((a, b) => a.localeCompare(b))
  );

  filteredHistory = computed(() => {
    const q = this.historySearch().toLowerCase().trim();
    const dec = this.historyDecisionFilter();
    const period = this.historyPeriodFilter();
    const now = new Date();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const periodStart = period === 'Aujourd’hui'
      ? todayStart
      : now.getTime() - (period === '7 derniers jours' ? 7 : 30) * 24 * 60 * 60 * 1000;
    return this.history().filter(h => {
      const matchSearch = !q ||
        h.dossierReference.toLowerCase().includes(q) ||
        h.nomEntreprise.toLowerCase().includes(q);
      const matchDec = dec === 'Toutes les décisions' ||
        (dec === 'Validées' && h.decisionPrise === 'VALIDEE') ||
        (dec === 'Rejetées' && h.decisionPrise === 'REJETEE');
      const decidedAt = h.decidedAt ? new Date(h.decidedAt).getTime() : Number.NaN;
      const matchPeriod = Number.isFinite(decidedAt) && decidedAt >= periodStart && decidedAt <= now.getTime();
      return matchSearch && matchDec && matchPeriod;
    });
  });

  // ─── Statistiques dashboard & KPIs ──────────────────────────
  validatedCount = computed(() => this.history().filter(h => h.decisionPrise === 'VALIDEE').length);
  rejectedCount = computed(() => this.history().filter(h => h.decisionPrise === 'REJETEE').length);
  totalTreatedCount = computed(() => this.history().length);

  todayValidated = computed(() => {
    const today = new Date().toDateString();
    return this.history().filter(h =>
      h.decisionPrise === 'VALIDEE' && h.decidedAt && new Date(h.decidedAt).toDateString() === today
    ).length;
  });

  todayRejected = computed(() => {
    const today = new Date().toDateString();
    return this.history().filter(h =>
      h.decisionPrise === 'REJETEE' && h.decidedAt && new Date(h.decidedAt).toDateString() === today
    ).length;
  });

  todayTreatedCount = computed(() => this.todayValidated() + this.todayRejected());

  resubmittedCount = computed(() =>
    this.queue().filter(item => item.isResubmitted).length
  );

  overdueCount = computed(() =>
    this.queue().filter(item => this.isOverdue(item)).length
  );

  nearDeadlineCount = computed(() =>
    this.queue().filter(item => this.isNearDeadline(item)).length
  );

  urgentCount = computed(() => this.overdueCount() + this.nearDeadlineCount());

  expiringCount = computed(() => this.nearDeadlineCount());

  validationRate = computed(() => {
    const total = this.totalTreatedCount();
    return total > 0 ? Math.round((this.validatedCount() / total) * 100) : 100;
  });

  rejectionRate = computed(() => {
    const total = this.totalTreatedCount();
    return total > 0 ? Math.round((this.rejectedCount() / total) * 100) : 0;
  });

  averageProcessingTimeHours = computed(() => {
    const treatedWithDates = this.history().filter(h => h.decidedAt && h.startedAt);
    if (treatedWithDates.length === 0) return 0;
    const totalHours = treatedWithDates.reduce((acc, h) => {
      const diff = (new Date(h.decidedAt).getTime() - new Date(h.startedAt!).getTime()) / 3600000;
      return acc + Math.max(0.2, diff);
    }, 0);
    return totalHours / treatedWithDates.length;
  });

  formattedAverageProcessingTime = computed(() => {
    const hours = this.averageProcessingTimeHours();
    if (hours === 0) {
      return this.totalTreatedCount() > 0 ? '1.5 j' : '—';
    }
    if (hours < 1) return `${Math.max(1, Math.round(hours * 60))} min`;
    if (hours < 24) return `${hours.toFixed(1)} h`;
    const days = (hours / 24).toFixed(1);
    return `${days} j`;
  });

  // ─── États de chargement ──────────────────────────────────
  isLoadingQueue = signal(true);
  isLoadingDetail = signal(false);
  isLoadingHistory = signal(true);
  isValidating = signal(false);
  isRejecting = signal(false);

  // ─── Formulaires & Modals ─────────────────────────────────
  showValidateConfirm = signal(false);
  notesValidation = '';
  showRejectForm = signal(false);
  motifRejet = '';
  motifRejetError: string | null = null;

  // ─── Toast ────────────────────────────────────────────────
  toastMessage = signal<string | null>(null);
  toastType = signal<'success' | 'error' | 'info'>('info');

  // ─── Libellés des organismes ──────────────────────────────
  readonly organismeLabels: Record<string, string> = {
    'OMPIC': 'Office Marocain de la Propriété Industrielle et Commerciale',
    'CRI': 'Centre Régional d\'Investissement',
    'TRIBUNAL': 'Tribunal de Commerce',
    'DGI': 'Direction Générale des Impôts',
    'CNSS': 'Caisse Nationale de Sécurité Sociale',
    'JOURNAL': 'Journal d\'Annonces Légales & BO',
    'GUICHET': 'Guichet Unique'
  };

  readonly organismeShortLabels: Record<string, string> = {
    'OMPIC': 'Office Marocain de la Propriété Industrielle',
    'CRI': 'Centre Régional d\'Investissement',
    'TRIBUNAL': 'Tribunal de Commerce',
    'DGI': 'Direction Générale des Impôts',
    'CNSS': 'Caisse Nationale de Sécurité Sociale',
    'JOURNAL': 'Journal d\'Annonces Légales',
    'GUICHET': 'Guichet Unique'
  };

  readonly today = new Date();

  // ─── Lifecycle ────────────────────────────────────────────
  ngOnInit(): void {
    this.loadQueue();
    this.loadHistorySilent();
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    if (this.isProfileMenuOpen) this.isProfileMenuOpen = false;
    if (this.isAgentVariantOpen) this.isAgentVariantOpen = false;
  }

  toggleProfileMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.isProfileMenuOpen = !this.isProfileMenuOpen;
  }

  toggleAgentVariant(event: MouseEvent): void {
    event.stopPropagation();
    this.isAgentVariantOpen = !this.isAgentVariantOpen;
  }

  setTab(tab: AgentTab): void {
    this.activeTab.set(tab);
    if (tab === 'queue') this.loadQueue();
    else if (tab === 'history') this.loadHistory();
  }

  // ─── Toast ────────────────────────────────────────────────
  showToast(message: string, type: 'success' | 'error' | 'info' = 'success'): void {
    this.toastMessage.set(message);
    this.toastType.set(type);
    setTimeout(() => this.toastMessage.set(null), 4500);
  }

  // ──────────────────────────────────────────────────────────
  // FILE D'ATTENTE
  // ──────────────────────────────────────────────────────────

  loadQueue(): void {
    this.isLoadingQueue.set(true);
    this.agentService.getQueue().subscribe({
      next: (data) => { this.queue.set(data); this.isLoadingQueue.set(false); },
      error: () => {
        this.showToast('Impossible de charger la file d\'attente.', 'error');
        this.isLoadingQueue.set(false);
      }
    });
  }

  takeInCharge(item: AgentQueueItem, event?: Event): void {
    event?.stopPropagation();
    if (item.claimedByAgentId && !item.claimedByCurrentAgent) return;
    this.isTakingCharge.set(true);
    this.agentService.takeInCharge(item.dossierId).subscribe({
      next: (claimedItem) => {
        this.isTakingCharge.set(false);
        this.updateQueueItem(claimedItem);
        this.showToast(`Dossier ${claimedItem.dossierReference} pris en charge.`, 'success');
        this.openDossier(claimedItem);
      },
      error: (err) => {
        this.isTakingCharge.set(false);
        this.loadQueue();
        this.showToast(err.error?.message || 'Impossible de prendre ce dossier en charge.', 'error');
      }
    });
  }

  takeSelectedDossierInCharge(): void {
    const item = this.selectedQueueItem();
    if (item) this.takeInCharge(item);
  }

  releaseSelectedDossierClaim(): void {
    const item = this.selectedQueueItem();
    if (!item) return;
    this.isReleasingClaim.set(true);
    this.agentService.releaseClaim(item.dossierId).subscribe({
      next: () => {
        this.isReleasingClaim.set(false);
        const released = {
          ...item,
          claimedByAgentId: null,
          claimedByAgentName: null,
          claimedAt: null,
          claimedByCurrentAgent: false
        };
        this.updateQueueItem(released);
        this.selectedQueueItem.set(released);
        this.showToast(`Prise en charge du dossier ${item.dossierReference} libérée.`, 'info');
      },
      error: (err) => {
        this.isReleasingClaim.set(false);
        this.showToast(err.error?.message || 'Impossible de libérer ce dossier.', 'error');
      }
    });
  }

  private updateQueueItem(updated: AgentQueueItem): void {
    this.queue.update(items => items.map(item =>
      item.dossierId === updated.dossierId ? updated : item
    ));
    if (this.selectedQueueItem()?.dossierId === updated.dossierId) {
      this.selectedQueueItem.set(updated);
    }
  }

  onQueueItemUpdated(item: AgentQueueItem): void {
    this.updateQueueItem(item);
  }

  // ──────────────────────────────────────────────────────────
  // DÉTAIL DOSSIER
  // ──────────────────────────────────────────────────────────

  openDossierById(dossierId: string, optionalItem?: AgentQueueItem, fromTab: 'queue' | 'history' = 'queue'): void {
    this.returnTab.set(fromTab);
    this.selectedQueueItem.set(optionalItem || null);
    this.selectedDossierId.set(dossierId);
    this.selectedDossier.set(null);
    this.selectedDocuments.set([]);
    this.activeTab.set('detail');
    this.isLoadingDetail.set(false);
    this.resetActionForms();
  }

  onReviewCompleted(event: { reference: string; decision: 'VALIDEE' | 'REJETEE' }): void {
    const verb = event.decision === 'VALIDEE' ? 'validé' : 'rejeté';
    this.showToast(`Dossier ${event.reference} ${verb} avec succès.`, 'success');
    window.setTimeout(() => this.setTab(this.returnTab()), 1200);
  }

  openDossier(item: AgentQueueItem): void {
    if (item.claimedByAgentId && !item.claimedByCurrentAgent) {
      this.showToast(`Ce dossier est déjà pris en charge par ${item.claimedByAgentName || 'un autre agent'}.`, 'info');
      return;
    }
    this.openDossierById(item.dossierId, item, 'queue');
  }

  openHistoryDossier(h: AgentHistoryItem): void {
    this.openDossierById(h.dossierId, undefined, 'history');
  }

  goBack(): void {
    this.setTab(this.returnTab());
  }

  loadDocuments(dossierId: string): void {
    this.agentService.getDocuments(dossierId).subscribe({
      next: (docs) => { this.selectedDocuments.set(docs); this.isLoadingDetail.set(false); },
      error: () => { this.selectedDocuments.set([]); this.isLoadingDetail.set(false); }
    });
  }

  downloadDocument(dossierId: string, docId: string, fileName: string): void {
    this.agentService.downloadDocument(dossierId, docId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = fileName; a.click();
        URL.revokeObjectURL(url);
        this.showToast(`Téléchargement de « ${fileName} » lancé.`, 'info');
      },
      error: () => this.showToast('Erreur lors du téléchargement.', 'error')
    });
  }

  openValidateConfirm(): void {
    this.showValidateConfirm.set(true);
    this.showRejectForm.set(false);
  }

  submitValidate(): void {
    const dossier = this.selectedDossier();
    if (!dossier) return;
    this.isValidating.set(true);
    const request = {
      notes: this.notesValidation.trim() || undefined,
      checklist: {},
      stepResult: {}
    };
    this.agentService.validateStep(dossier.id, request).subscribe({
      next: () => {
        this.isValidating.set(false);
        this.showValidateConfirm.set(false);
        this.notesValidation = '';
        this.showToast(`Dossier ${dossier.reference} validé avec succès !`, 'success');
        setTimeout(() => this.setTab(this.returnTab()), 1200);
      },
      error: (err) => {
        this.isValidating.set(false);
        this.showToast(err.error?.message || 'Erreur lors de la validation.', 'error');
      }
    });
  }

  openRejectForm(): void {
    this.showRejectForm.set(true);
    this.showValidateConfirm.set(false);
    this.motifRejet = '';
    this.motifRejetError = null;
  }

  submitReject(): void {
    this.motifRejetError = null;
    const motif = this.motifRejet.trim();
    if (!motif || motif.length < 10) {
      this.motifRejetError = 'Le motif doit contenir au moins 10 caractères (RG8).';
      return;
    }
    if (motif.length > 500) {
      this.motifRejetError = 'Le motif ne peut pas dépasser 500 caractères.';
      return;
    }
    const dossier = this.selectedDossier();
    if (!dossier) return;
    this.isRejecting.set(true);
    this.agentService.rejectStep(dossier.id, { motifRejet: motif }).subscribe({
      next: () => {
        this.isRejecting.set(false);
        this.showRejectForm.set(false);
        this.motifRejet = '';
        this.showToast(`Dossier ${dossier.reference} rejeté — entrepreneur notifié.`, 'info');
        setTimeout(() => this.setTab(this.returnTab()), 1200);
      },
      error: (err) => {
        this.isRejecting.set(false);
        this.showToast(err.error?.message || 'Erreur lors du rejet.', 'error');
      }
    });
  }

  // ──────────────────────────────────────────────────────────
  // HISTORIQUE
  // ──────────────────────────────────────────────────────────

  loadHistory(): void {
    this.isLoadingHistory.set(true);
    this.agentService.getHistory().subscribe({
      next: (data) => { this.history.set(data); this.isLoadingHistory.set(false); },
      error: () => {
        this.showToast('Impossible de charger l\'historique.', 'error');
        this.isLoadingHistory.set(false);
      }
    });
  }

  private loadHistorySilent(): void {
    this.agentService.getHistory().subscribe({
      next: (data) => {
        this.history.set(data);
        this.isLoadingHistory.set(false);
      },
      error: () => {
        this.isLoadingHistory.set(false);
      }
    });
  }

  private resetActionForms(): void {
    this.showValidateConfirm.set(false);
    this.showRejectForm.set(false);
    this.notesValidation = '';
    this.motifRejet = '';
    this.motifRejetError = null;
  }

  // ─── Helpers ──────────────────────────────────────────────

  getOrganismeLabel(code: string | null): string {
    if (!code) return 'Guichet Unique';
    return this.organismeLabels[code] || code;
  }

  getOrganismeShortLabel(code: string | null): string {
    if (!code) return 'Guichet Unique';
    return this.organismeShortLabels[code] || code;
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  getWaitHours(startedAt: string | null): number {
    if (!startedAt) return 0;
    return Math.floor((Date.now() - new Date(startedAt).getTime()) / 3600000);
  }

  getWaitLabel(startedAt: string | null): string {
    const hours = this.getWaitHours(startedAt);
    if (hours < 1) return '< 1 h';
    if (hours < 24) return `${hours} h`;
    const days = Math.floor(hours / 24);
    const rem = hours % 24;
    return rem > 0 ? `${days} j ${rem} h` : `${days} j`;
  }

  getIndicativeHours(item: AgentQueueItem | null | undefined): number {
    if (!item) return 48;
    return (item.indicativeDurationDays ?? 2) * 24;
  }

  getRemainingLabel(item: AgentQueueItem): string {
    if (!item.deadlineAt) return '—';
    const remainingMs = new Date(item.deadlineAt).getTime() - Date.now();
    if (remainingMs <= 0) {
      const lateHours = Math.floor(Math.abs(remainingMs) / 3600000);
      return `Dépassé de ${lateHours < 24 ? `${lateHours} h` : `${Math.floor(lateHours / 24)} j`}`;
    }
    const remainingHours = Math.ceil(remainingMs / 3600000);
    return remainingHours < 24
      ? `${remainingHours} h`
      : `${Math.floor(remainingHours / 24)} j ${remainingHours % 24} h`;
  }

  isOverdue(item: AgentQueueItem): boolean {
    const hours = this.getWaitHours(item.etapeStartedAt);
    return hours >= this.getIndicativeHours(item);
  }

  isNearDeadline(item: AgentQueueItem): boolean {
    const hours = this.getWaitHours(item.etapeStartedAt);
    const max = this.getIndicativeHours(item);
    return hours >= (max * 0.7) && hours < max;
  }

  isUrgent(startedAt: string | null): boolean {
    return this.getWaitHours(startedAt) >= 24;
  }

  isExpiring(startedAt: string | null): boolean {
    return this.getWaitHours(startedAt) >= 48;
  }

  getStatusBadgeClass(statut: string): string {
    const map: Record<string, string> = {
      'EN_COURS': 'badge-encours',
      'SOUMIS': 'badge-soumis',
      'A_CORRIGER': 'badge-corriger',
      'FINALISE': 'badge-finalise',
      'BROUILLON': 'badge-brouillon'
    };
    return map[statut] || 'badge-default';
  }

  getStatusLabel(statut: string): string {
    const map: Record<string, string> = {
      'EN_COURS': 'En cours',
      'SOUMIS': 'Soumis',
      'A_CORRIGER': 'À corriger',
      'FINALISE': 'Finalisé',
      'BROUILLON': 'Brouillon'
    };
    return map[statut] || statut;
  }

  getStepStatusClass(statut: string): string {
    const map: Record<string, string> = {
      'VALIDEE': 'step-validee',
      'EN_COURS': 'step-encours',
      'EN_ATTENTE': 'step-attente',
      'REJETEE': 'step-rejetee'
    };
    return map[statut] || '';
  }

  getFormeLabel(forme: string): string {
    const map: Record<string, string> = {
      'SARL': 'SARL',
      'SARL_AU': 'SARL-AU',
      'SA': 'SA',
      'AUTO_ENTREPRENEUR': 'Auto-Entrepreneur'
    };
    return map[forme] || forme;
  }

  getDecisionBadgeClass(decision: string): string {
    return decision === 'VALIDEE' ? 'dec-validee' : 'dec-rejetee';
  }

  getDecisionLabel(item: AgentHistoryItem): string {
    const action = item.decisionPrise === 'VALIDEE' ? 'validé' : 'rejeté';
    const label = item.etapeLibelle || 'Étape';
    return `● ${label} ${action}`;
  }

  getTreatmentDuration(item: AgentHistoryItem): string {
    if (!item.startedAt || !item.decidedAt) return '—';
    const durationMs = new Date(item.decidedAt).getTime() - new Date(item.startedAt).getTime();
    if (!Number.isFinite(durationMs) || durationMs < 0) return '—';
    const totalHours = Math.floor(durationMs / 3600000);
    const days = Math.floor(totalHours / 24);
    const hours = totalHours % 24;
    if (days === 0 && hours === 0) return '< 1 h';
    return [days > 0 ? `${days} j` : '', hours > 0 ? `${hours} h` : ''].filter(Boolean).join(' ');
  }

  canTakeAction = computed(() => {
    const d = this.selectedDossier();
    if (!d) return false;
    const statut = d.statut || d.status;
    if (statut === 'FINALISE' || statut === 'BROUILLON') return false;
    const active = d.steps?.find(s => s.status === 'EN_COURS');
    return !!active
      && active.actionOwner === 'AGENT'
      && this.selectedQueueItem()?.claimedByCurrentAgent === true;
  });

  getDossierNom(d: DossierDetail | null): string {
    return d?.nomEntreprise || d?.proposedCompanyName || '';
  }

  getDossierForme(d: DossierDetail | null): string {
    return this.getFormeLabel(d?.formeJuridique || d?.legalFormCode || '');
  }

  getDossierStatut(d: DossierDetail | null): string {
    return d?.statut || d?.status || '';
  }

  getDossierCapital(d: DossierDetail | null): number {
    return d?.capitalMad ?? d?.capitalAmountMad ?? 0;
  }

  getDossierActivite(d: DossierDetail | null): string {
    return d?.descriptionActivite || d?.activityDescription || '';
  }

  getCheckedDocsCount(): number {
    return this.selectedDocuments().length;
  }

  getActiveStep(): StepTracking | null {
    const d = this.selectedDossier();
    if (!d) return null;
    return d.steps.find(s => s.status === 'EN_COURS') ||
           d.steps.find(s => s.status === 'REJETEE') ||
           null;
  }

  getProgressSteps() {
    const d = this.selectedDossier();
    if (!d) return [];
    const agent = this.currentOrganisme();
    return d.steps.filter(s => s.actionOwner === 'AGENT' && s.organizationCode === agent)
      .length > 0
      ? d.steps
      : d.steps;
  }

  getDashboardRecentQueue(): AgentQueueItem[] {
    return this.queue().slice(0, 3);
  }

  logout(): void {
    this.authService.logout().subscribe();
  }
}
