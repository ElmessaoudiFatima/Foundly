/**
 * Modèles TypeScript pour la gestion des workflows de création d'entreprise.
 * Correspondent aux DTOs du backend WorkflowVersion + EtapeWorkflow.
 */

export type WorkflowStatus = 'BROUILLON' | 'ACTIVE' | 'ARCHIVEE';
export type LegalFormCode = 'AUTO_ENTREPRENEUR' | 'SARL' | 'SARL_AU' | 'SA';

export interface EtapeWorkflowDto {
  id: string;
  ordre: number;
  code: string;
  libelle: string;
  organismeCode: string | null;
  organismeLibelle: string | null;
  fraisMad: number;
  delaiIndicatif: string | null;
  conditionApplication: string | null;
  obligatoire: boolean;
  externe: boolean;
}

export interface WorkflowVersionSummaryDto {
  id: string;
  formeJuridique: LegalFormCode;
  formeJuridiqueLabel: string;
  numeroVersion: string;
  statut: WorkflowStatus;
  statutLabel: string;
  nombreEtapes: number;
  dateModification: string;
}

export interface WorkflowVersionDetailDto {
  id: string;
  formeJuridique: LegalFormCode;
  formeJuridiqueLabel: string;
  numeroVersion: string;
  statut: WorkflowStatus;
  statutLabel: string;
  description: string | null;
  dateModification: string;
  dateCreation: string;
  etapes: EtapeWorkflowDto[];
}

export interface CreateWorkflowVersionRequest {
  formeJuridique: LegalFormCode;
  description?: string;
  dupliquerDepuis?: string | null; // UUID de la version à dupliquer
}

export interface UpdateEtapeRequest {
  libelle: string;
  organismeCode?: string | null;
  organismeLibelle?: string | null;
  fraisMad?: number;
  delaiIndicatif?: string | null;
  conditionApplication?: string | null;
  obligatoire: boolean;
  externe: boolean;
}

export interface AddEtapeRequest {
  code: string;
  libelle: string;
  organismeCode?: string | null;
  organismeLibelle?: string | null;
  fraisMad?: number;
  delaiIndicatif?: string | null;
  conditionApplication?: string | null;
  obligatoire?: boolean;
  externe?: boolean;
}

export interface ReorderEtapesRequest {
  etapeIds: string[]; // liste ordonnée des IDs d'étapes
}

