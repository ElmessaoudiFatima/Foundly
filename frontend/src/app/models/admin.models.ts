export interface MonthlyVolumeItem {
  month: string;
  count: number;
}

export interface FormeDistributionItem {
  code: string;
  label: string;
  count: number;
  percentage: number;
  color: string;
}

export interface AdminDashboard {
  totalDossiers: number;
  totalDossiersChange: string;
  dossiersEnCours: number;
  dossiersEnCoursPct: string;
  dossiersFinalises: number;
  dossiersFinalisesChange: string;
  tauxRejet: number;
  tauxRejetChange: string;
  dureeMoyenneJours: number;
  dureeMoyenneChange: string;
  monthlyTrendPct: string;
  monthlyVolume: MonthlyVolumeItem[];
  repartitionFormes: FormeDistributionItem[];
}

export interface DossierAdminItem {
  id: string;
  reference: string;
  companyName: string;
  entrepreneurName: string;
  entrepreneurCinMasked: string;
  legalForm: string;
  legalFormLabel: string;
  region: string;
  currentStep: string;
  status: 'BROUILLON' | 'SOUMIS' | 'EN_COURS' | 'A_CORRIGER' | 'FINALISE';
  statusLabel: string;
  createdAt: string;
  updatedAt: string;
  createdFormatted: string;
  updatedFormatted: string;
}

export interface PagedDossiers {
  content: DossierAdminItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AgentAdminItem {
  id: string;
  firstName: string;
  lastName: string;
  fullName: string;
  initials: string;
  email: string;
  organization: string;
  active: boolean;
  lastActivityFormatted: string;
  lastActivityAt?: string;
  createdAt: string;
}

export interface PagedAgents {
  content: AgentAdminItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateAgentPayload {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  organization: string;
  phone?: string;
  cin?: string;
}

export interface OrganismeItem {
  code: string;
  name: string;
  description: string;
  activeAgentCount: number;
  managedSteps: string[];
}

export interface OrganismeCreatePayload {
  name: string;
  code: string;
  description: string;
}

export interface OrganismeUpdatePayload {
  name?: string;
  description?: string;
}
