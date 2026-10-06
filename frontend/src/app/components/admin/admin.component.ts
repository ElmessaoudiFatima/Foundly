import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { AdminService } from '../../services/admin.service';
import {
  AdminDashboard,
  DossierAdminItem,
  PagedDossiers,
  AgentAdminItem,
  PagedAgents,
  CreateAgentPayload,
  OrganismeItem,
  OrganismeCreatePayload,
  OrganismeUpdatePayload
} from '../../models/admin.models';

type AdminTab = 'dashboard' | 'dossiers' | 'agents' | 'organismes' | 'workflows' | 'exports' | 'profile';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.css']
})
export class AdminComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly adminService = inject(AdminService);
  private readonly router = inject(Router);

  readonly user = this.authService.currentUser;

  // Active section
  activeTab: AdminTab = 'dashboard';

  // Toast feedback
  toastMessage = '';
  private toastTimer?: ReturnType<typeof setTimeout>;

  // Loading states
  isLoadingDashboard = false;
  isLoadingDossiers = false;
  isLoadingAgents = false;
  isSubmittingAgent = false;
  isExporting = false;

  // 1. Dashboard data (chargé depuis l'API / base de données)
  dashboard: AdminDashboard = this.emptyDashboard();
  selectedPeriod = '6 derniers mois';
  selectedRegionDashboard = 'Toutes les régions';
  readonly currentYear = new Date().getFullYear();

  private dossierSearchTimer?: ReturnType<typeof setTimeout>;
  private agentSearchTimer?: ReturnType<typeof setTimeout>;

  // 2. Dossiers data
  dossiers: DossierAdminItem[] = [];
  dossiersTotalElements = 0;
  dossiersTotalPages = 1;
  dossiersPage = 0;
  dossiersPageSize = 10;

  // Dossiers filters
  searchDossier = '';
  filterStatus = 'Tous les statuts';
  filterForme = 'Toutes les formes';
  filterRegion = 'Toutes les régions';
  filterEtape = 'Toutes les étapes';

  // Selected dossier for view details modal
  selectedDossier: DossierAdminItem | null = null;

  // 3. Agents data
  agents: AgentAdminItem[] = [];
  agentsTotalElements = 0;
  agentsTotalPages = 1;
  agentsPage = 0;
  agentsPageSize = 10;

  // Agents filters
  searchAgent = '';
  filterOrganization = 'Tous les organismes';
  filterAgentStatus = 'Tous les statuts';

  // Agent creation modal
  isCreateAgentModalOpen = false;
  newAgent: CreateAgentPayload = {
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    organization: 'CRI Casablanca-Settat',
    phone: '',
    cin: ''
  };
  createAgentError = '';

  // View agent modal
  selectedAgent: AgentAdminItem | null = null;

  // ===================== ORGANISMES =====================
  organismes: OrganismeItem[] = [];
  isLoadingOrganismes = false;

  // Modal: créer un organisme
  isCreateOrganismeModalOpen = false;
  isSubmittingOrganisme = false;
  createOrganismeError = '';
  newOrganisme: OrganismeCreatePayload = { name: '', code: '', description: '' };

  // Modal: configurer (PATCH) un organisme
  isConfigureOrganismeModalOpen = false;
  isSubmittingConfigOrganisme = false;
  configOrganismeError = '';
  configuringOrganisme: OrganismeItem | null = null;
  configOrganismeForm: OrganismeUpdatePayload = { name: '', description: '' };

  ngOnInit(): void {
    this.loadDashboard();
    this.loadDossiers();
    this.loadAgents();
  }

  get userInitials(): string {
    const u = this.user();
    const a = u?.firstName?.charAt(0) || 'A';
    const b = u?.lastName?.charAt(0) || 'D';
    return (a + b).toUpperCase();
  }

  get nationalViewDate(): string {
    return new Intl.DateTimeFormat('fr-FR', {
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    }).format(new Date());
  }

  get topLegalFormLabel(): string {
    const forms = this.dashboard.repartitionFormes;
    if (!forms?.length) return '—';
    const top = forms.reduce((best, f) => (f.percentage > best.percentage ? f : best), forms[0]);
    return top.label;
  }

  get topLegalFormPct(): number {
    const forms = this.dashboard.repartitionFormes;
    if (!forms?.length) return 0;
    return forms.reduce((best, f) => (f.percentage > best.percentage ? f : best), forms[0]).percentage;
  }

  private emptyDashboard(): AdminDashboard {
    return {
      totalDossiers: 0,
      totalDossiersChange: '—',
      dossiersEnCours: 0,
      dossiersEnCoursPct: '—',
      dossiersFinalises: 0,
      dossiersFinalisesChange: '—',
      tauxRejet: 0,
      tauxRejetChange: '—',
      dureeMoyenneJours: 0,
      dureeMoyenneChange: '—',
      monthlyTrendPct: '—',
      monthlyVolume: [],
      repartitionFormes: []
    };
  }

  setTab(tab: AdminTab): void {
    this.activeTab = tab;
    if (tab === 'dashboard') {
      this.loadDashboard();
    } else if (tab === 'dossiers') {
      this.loadDossiers();
    } else if (tab === 'agents') {
      this.loadAgents();
    } else if (tab === 'organismes') {
      this.loadOrganismes();
    }
  }

  // ===================== DASHBOARD =====================
  loadDashboard(): void {
    this.isLoadingDashboard = true;
    this.adminService.getDashboard(this.selectedPeriod, this.selectedRegionDashboard).subscribe({
      next: (data) => {
        this.dashboard = data;
        this.isLoadingDashboard = false;
      },
      error: () => {
        this.isLoadingDashboard = false;
        this.showToast('Impossible de charger le tableau de bord.');
      }
    });
  }

  onDashboardFilterChange(): void {
    this.loadDashboard();
  }

  // ===================== DOSSIERS =====================
  loadDossiers(): void {
    this.isLoadingDossiers = true;
    const statusParam = this.mapStatusToEnum(this.filterStatus);
    const formParam = this.mapFormeToEnum(this.filterForme);

    this.adminService.getDossiers(
      this.searchDossier,
      statusParam,
      formParam,
      this.filterRegion,
      this.filterEtape,
      this.dossiersPage,
      this.dossiersPageSize
    ).subscribe({
      next: (res: PagedDossiers) => {
        this.dossiers = res.content ?? [];
        this.dossiersTotalElements = res.totalElements ?? 0;
        this.dossiersTotalPages = Math.max(res.totalPages ?? 1, 1);
        this.isLoadingDossiers = false;
      },
      error: () => {
        this.isLoadingDossiers = false;
        this.dossiers = [];
        this.showToast('Erreur lors du chargement des dossiers.');
      }
    });
  }

  onDossierFilterChange(): void {
    this.dossiersPage = 0;
    this.loadDossiers();
  }

  onDossierSearchChange(): void {
    this.dossiersPage = 0;
    if (this.dossierSearchTimer) {
      clearTimeout(this.dossierSearchTimer);
    }
    this.dossierSearchTimer = setTimeout(() => this.loadDossiers(), 300);
  }

  resetDossierFilters(): void {
    this.searchDossier = '';
    this.filterStatus = 'Tous les statuts';
    this.filterForme = 'Toutes les formes';
    this.filterRegion = 'Toutes les régions';
    this.filterEtape = 'Toutes les étapes';
    this.dossiersPage = 0;
    this.loadDossiers();
  }

  changeDossierPage(delta: number): void {
    const newPage = this.dossiersPage + delta;
    if (newPage >= 0 && newPage < this.dossiersTotalPages) {
      this.dossiersPage = newPage;
      this.loadDossiers();
    }
  }

  openDossierModal(dossier: DossierAdminItem): void {
    this.selectedDossier = dossier;
  }

  closeDossierModal(): void {
    this.selectedDossier = null;
  }

  exportDossiers(): void {
    this.isExporting = true;
    const statusParam = this.mapStatusToEnum(this.filterStatus);
    const formParam = this.mapFormeToEnum(this.filterForme);

    this.adminService.exportDossiersCsv(
      this.searchDossier,
      statusParam,
      formParam,
      this.filterRegion
    ).subscribe({
      next: (blob) => {
        this.isExporting = false;
        if (!blob || blob.size === 0) {
          this.showToast('Export vide : aucun dossier à exporter.');
          return;
        }
        // Si le serveur a renvoyé une erreur JSON sous forme de blob
        if (blob.type && blob.type.includes('application/json')) {
          blob.text().then(() => {
            this.showToast('Erreur lors du téléchargement de l\'export.');
          });
          return;
        }
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `dossiers-foundly-${new Date().toISOString().slice(0, 10)}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.showToast('Export CSV téléchargé avec succès.');
      },
      error: () => {
        this.isExporting = false;
        this.showToast('Erreur lors du téléchargement de l\'export.');
      }
    });
  }

  // ===================== AGENTS =====================
  loadAgents(): void {
    this.isLoadingAgents = true;
    let activeParam: boolean | null = null;
    if (this.filterAgentStatus === 'Actif') activeParam = true;
    if (this.filterAgentStatus === 'Désactivé') activeParam = false;

    this.adminService.getAgents(
      this.searchAgent,
      this.filterOrganization,
      activeParam,
      this.agentsPage,
      this.agentsPageSize
    ).subscribe({
      next: (res: PagedAgents) => {
        this.agents = res.content ?? [];
        this.agentsTotalElements = res.totalElements ?? 0;
        this.agentsTotalPages = Math.max(res.totalPages ?? 1, 1);
        this.isLoadingAgents = false;
      },
      error: () => {
        this.isLoadingAgents = false;
        this.agents = [];
        this.showToast('Erreur lors du chargement des agents.');
      }
    });
  }

  onAgentFilterChange(): void {
    this.agentsPage = 0;
    this.loadAgents();
  }

  onAgentSearchChange(): void {
    this.agentsPage = 0;
    if (this.agentSearchTimer) {
      clearTimeout(this.agentSearchTimer);
    }
    this.agentSearchTimer = setTimeout(() => this.loadAgents(), 300);
  }

  openCreateAgentModal(): void {
    this.newAgent = {
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      organization: 'CRI Casablanca-Settat',
      phone: '',
      cin: ''
    };
    this.createAgentError = '';
    this.isCreateAgentModalOpen = true;
  }

  closeCreateAgentModal(): void {
    this.isCreateAgentModalOpen = false;
    this.createAgentError = '';
  }

  submitCreateAgent(): void {
    if (!this.newAgent.firstName.trim() || !this.newAgent.lastName.trim()) {
      this.createAgentError = 'Le prénom et le nom sont requis.';
      return;
    }
    if (!this.newAgent.email.trim()) {
      this.createAgentError = 'L\'adresse e-mail est requise.';
      return;
    }
    if (!this.newAgent.password || this.newAgent.password.length < 8) {
      this.createAgentError = 'Le mot de passe doit comporter au moins 8 caractères.';
      return;
    }

    this.isSubmittingAgent = true;
    this.createAgentError = '';

    this.adminService.createAgent(this.newAgent).subscribe({
      next: (created) => {
        this.isSubmittingAgent = false;
        this.isCreateAgentModalOpen = false;
        this.showToast(`Compte agent « ${created.fullName} » créé avec succès et enregistré en base de données.`);
        this.loadAgents();
      },
      error: (err) => {
        this.isSubmittingAgent = false;
        if (err.status === 409) {
          this.createAgentError = 'Cette adresse e-mail est déjà utilisée par un autre compte.';
        } else {
          this.createAgentError = err.error?.message || 'Erreur lors de la création de l\'agent. Veuillez vérifier les champs.';
        }
      }
    });
  }

  toggleAgentStatus(agent: AgentAdminItem): void {
    if (!agent?.id) {
      this.showToast('Identifiant agent manquant.');
      return;
    }

    const newStatus = !agent.active;
    const actionLabel = newStatus ? 'activé' : 'désactivé';

    this.adminService.updateAgentStatus(agent.id, newStatus).subscribe({
      next: (updated) => {
        agent.active = updated.active;
        if (this.selectedAgent?.id === agent.id) {
          this.selectedAgent = { ...this.selectedAgent, active: updated.active };
        }
        this.showToast(`Compte de l'agent ${agent.fullName} ${actionLabel} avec succès.`);
        this.loadAgents();
      },
      error: (err) => {
        const msg = err?.error?.message || err?.message || 'Erreur lors du changement de statut de l\'agent.';
        this.showToast(typeof msg === 'string' ? msg : 'Erreur lors du changement de statut de l\'agent.');
      }
    });
  }

  openAgentDetails(agent: AgentAdminItem): void {
    this.selectedAgent = agent;
  }

  closeAgentDetails(): void {
    this.selectedAgent = null;
  }

  logout(): void {
    this.authService.logout().subscribe();
  }

  // ===================== ORGANISMES LOGIC =====================
  loadOrganismes(): void {
    this.isLoadingOrganismes = true;
    this.adminService.getOrganismes().subscribe({
      next: (data) => {
        this.organismes = data;
        this.isLoadingOrganismes = false;
      },
      error: () => {
        this.isLoadingOrganismes = false;
        this.showToast('Impossible de charger les organismes.');
      }
    });
  }

  openCreateOrganismeModal(): void {
    this.newOrganisme = { name: '', code: '', description: '' };
    this.createOrganismeError = '';
    this.isCreateOrganismeModalOpen = true;
  }

  closeCreateOrganismeModal(): void {
    this.isCreateOrganismeModalOpen = false;
    this.createOrganismeError = '';
  }

  submitCreateOrganisme(): void {
    if (!this.newOrganisme.name.trim() || !this.newOrganisme.code.trim() || !this.newOrganisme.description.trim()) {
      this.createOrganismeError = 'Tous les champs sont obligatoires.';
      return;
    }
    this.isSubmittingOrganisme = true;
    this.createOrganismeError = '';
    const payload: OrganismeCreatePayload = {
      name: this.newOrganisme.name.trim(),
      code: this.newOrganisme.code.trim().toUpperCase(),
      description: this.newOrganisme.description.trim()
    };
    this.adminService.createOrganisme(payload).subscribe({
      next: (created) => {
        this.organismes = [...this.organismes, created];
        this.isSubmittingOrganisme = false;
        this.closeCreateOrganismeModal();
        this.showToast(`Organisme « ${created.name} » créé avec succès.`);
      },
      error: (err) => {
        this.isSubmittingOrganisme = false;
        const msg = err?.error?.message || 'Erreur lors de la création de l\'organisme.';
        this.createOrganismeError = typeof msg === 'string' ? msg : 'Erreur lors de la création.';
      }
    });
  }

  openConfigureOrganisme(org: OrganismeItem): void {
    this.configuringOrganisme = org;
    this.configOrganismeForm = { name: org.name, description: org.description };
    this.configOrganismeError = '';
    this.isConfigureOrganismeModalOpen = true;
  }

  closeConfigureOrganisme(): void {
    this.isConfigureOrganismeModalOpen = false;
    this.configuringOrganisme = null;
    this.configOrganismeError = '';
  }

  submitUpdateOrganisme(): void {
    if (!this.configuringOrganisme) return;
    if (!this.configOrganismeForm.name?.trim() && !this.configOrganismeForm.description?.trim()) {
      this.configOrganismeError = 'Au moins un champ (Nom ou Description) est requis.';
      return;
    }
    this.isSubmittingConfigOrganisme = true;
    this.configOrganismeError = '';
    const payload: OrganismeUpdatePayload = {};
    if (this.configOrganismeForm.name?.trim()) payload.name = this.configOrganismeForm.name.trim();
    if (this.configOrganismeForm.description?.trim() !== undefined) payload.description = this.configOrganismeForm.description?.trim();
    this.adminService.updateOrganisme(this.configuringOrganisme.code, payload).subscribe({
      next: (updated) => {
        this.organismes = this.organismes.map(o => o.code === updated.code ? updated : o);
        this.isSubmittingConfigOrganisme = false;
        this.showToast(`Organisme « ${updated.name} » mis à jour.`);
        this.closeConfigureOrganisme();
      },
      error: (err) => {
        this.isSubmittingConfigOrganisme = false;
        const msg = err?.error?.message || 'Erreur lors de la mise à jour.';
        this.configOrganismeError = typeof msg === 'string' ? msg : 'Erreur lors de la mise à jour.';
      }
    });
  }

  getOrganismeInitials(org: OrganismeItem): string {
    return org.code.substring(0, 3);
  }

  showToast(message: string): void {
    this.toastMessage = message;
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
    this.toastTimer = setTimeout(() => {
      this.toastMessage = '';
    }, 3800);
  }

  // ===================== MAPPINGS =====================
  private mapStatusToEnum(label: string): string | undefined {
    switch (label) {
      case 'En cours': return 'EN_COURS';
      case 'À corriger': return 'A_CORRIGER';
      case 'Finalisé': return 'FINALISE';
      case 'Soumis': return 'SOUMIS';
      case 'Brouillon': return 'BROUILLON';
      default: return undefined;
    }
  }

  private mapFormeToEnum(label: string): string | undefined {
    switch (label) {
      case 'SARL': return 'SARL';
      case 'SARL-AU': return 'SARL_AU';
      case 'Auto-entrepreneur': return 'AUTO_ENTREPRENEUR';
      case 'SA': return 'SA';
      default: return undefined;
    }
  }

  // Chart helpers
  private getMonthlyChartMax(): number {
    const items = this.dashboard.monthlyVolume;
    if (!items?.length) return 1;
    const peak = Math.max(...items.map(i => i.count));
    return Math.max(peak, 1);
  }

  getMonthlyPoints(): string {
    const items = this.dashboard.monthlyVolume;
    if (!items || items.length === 0) return '';
    const max = this.getMonthlyChartMax();
    const width = 580;
    const height = 190;
    const startX = 30;
    const usableW = width - 60;
    const step = items.length > 1 ? usableW / (items.length - 1) : 0;

    const points = items.map((item, index) => {
      const x = startX + index * step;
      const y = height - 25 - ((item.count / max) * (height - 60));
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    });

    return points.join(' ');
  }

  getMonthlyPointCy(index: number, count: number): number {
    const max = this.getMonthlyChartMax();
    return 165 - 25 - ((count / max) * 105);
  }

  getMonthlyPointCx(index: number): number {
    const items = this.dashboard.monthlyVolume;
    const len = items?.length ?? 0;
    if (len <= 1) return 30;
    return 30 + index * (520 / (len - 1));
  }

  getDonutCircumference(): number {
    return 2 * Math.PI * 54;
  }

  getDonutSegments(): { color: string; dashArray: string; dashOffset: number }[] {
    const formes = this.dashboard.repartitionFormes;
    const C = this.getDonutCircumference();
    if (!formes?.length) return [];

    let offset = 0;
    return formes.map(f => {
      const length = (f.percentage / 100) * C;
      const segment = {
        color: f.color,
        dashArray: `${length} ${C - length}`,
        dashOffset: -offset
      };
      offset += length;
      return segment;
    });
  }

  getMonthlyAreaPath(): string {
    const points = this.getMonthlyPoints().split(' ');
    if (points.length === 0) return '';
    const firstPoint = points[0];
    const lastPoint = points[points.length - 1];
    const firstX = firstPoint.split(',')[0];
    const lastX = lastPoint.split(',')[0];
    const baselineY = 165;

    return `M ${firstX},${baselineY} L ${points.join(' L ')} L ${lastX},${baselineY} Z`;
  }

  getMonthlyCurvePath(): string {
    const points = this.getMonthlyPoints().split(' ');
    if (points.length === 0) return '';
    return `M ${points.join(' L ')}`;
  }
}
