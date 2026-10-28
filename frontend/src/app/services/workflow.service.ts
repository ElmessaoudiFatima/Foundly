import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  WorkflowVersionSummaryDto,
  WorkflowVersionDetailDto,
  CreateWorkflowVersionRequest,
  UpdateEtapeRequest,
  AddEtapeRequest,
  ReorderEtapesRequest,
  EtapeWorkflowDto,
  LegalFormCode
} from '../models/workflow.models';

/**
 * Service Angular pour la gestion des workflows de création d'entreprise.
 * Consomme les endpoints REST /admin/workflows.
 *
 * Architecture :
 * - Toutes les opérations de lecture/écriture passent par ce service.
 * - La gestion des erreurs HTTP est déléguée aux composants (subscribe avec error handler).
 * - Conforme à RG14 : les modifications de workflow ne s'appliquent qu'aux nouveaux dossiers.
 */
@Injectable({
  providedIn: 'root'
})
export class WorkflowService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/admin/workflows`;

  /**
   * Liste toutes les versions de workflow.
   * @param formeJuridique filtre optionnel par forme juridique
   */
  getAllVersions(formeJuridique?: LegalFormCode): Observable<WorkflowVersionSummaryDto[]> {
    let params = new HttpParams();
    if (formeJuridique) {
      params = params.set('formeJuridique', formeJuridique);
    }
    return this.http.get<WorkflowVersionSummaryDto[]>(this.baseUrl, { params });
  }

  /**
   * Récupère le détail d'une version (avec ses étapes).
   */
  getVersionById(id: string): Observable<WorkflowVersionDetailDto> {
    return this.http.get<WorkflowVersionDetailDto>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }

  /**
   * Crée une nouvelle version de workflow.
   * Si dupliquerDepuis est fourni, les étapes sont copiées depuis cette version.
   */
  createVersion(request: CreateWorkflowVersionRequest): Observable<WorkflowVersionDetailDto> {
    return this.http.post<WorkflowVersionDetailDto>(this.baseUrl, request);
  }

  /**
   * Publie une version Brouillon → Active.
   * L'ancienne version Active est automatiquement archivée (cf. RG14).
   * Action irréversible — demander confirmation avant d'appeler.
   */
  publishVersion(id: string): Observable<WorkflowVersionDetailDto> {
    return this.http.post<WorkflowVersionDetailDto>(`${this.baseUrl}/${encodeURIComponent(id)}/publish`, {});
  }

  /**
   * Archive une version Active → Archivée.
   * Action irréversible — demander confirmation avant d'appeler.
   */
  archiverVersion(id: string): Observable<WorkflowVersionDetailDto> {
    return this.http.post<WorkflowVersionDetailDto>(`${this.baseUrl}/${encodeURIComponent(id)}/archive`, {});
  }

  /**
   * Ajoute une étape à la fin du workflow (version Brouillon uniquement).
   */
  addEtape(versionId: string, request: AddEtapeRequest): Observable<EtapeWorkflowDto> {
    return this.http.post<EtapeWorkflowDto>(`${this.baseUrl}/${encodeURIComponent(versionId)}/etapes`, request);
  }

  /**
   * Met à jour une étape existante (version Brouillon uniquement).
   */
  updateEtape(versionId: string, etapeId: string, request: UpdateEtapeRequest): Observable<EtapeWorkflowDto> {
    return this.http.put<EtapeWorkflowDto>(
      `${this.baseUrl}/${encodeURIComponent(versionId)}/etapes/${encodeURIComponent(etapeId)}`,
      request
    );
  }

  /**
   * Supprime une étape (version Brouillon uniquement).
   */
  deleteEtape(versionId: string, etapeId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/${encodeURIComponent(versionId)}/etapes/${encodeURIComponent(etapeId)}`
    );
  }

  /**
   * Réordonne les étapes en fournissant la liste ordonnée des IDs.
   * (version Brouillon uniquement)
   */
  reorderEtapes(versionId: string, request: ReorderEtapesRequest): Observable<WorkflowVersionDetailDto> {
    return this.http.put<WorkflowVersionDetailDto>(
      `${this.baseUrl}/${encodeURIComponent(versionId)}/etapes/reorder`,
      request
    );
  }
}

