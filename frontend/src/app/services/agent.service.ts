import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AgentQueueItem,
  AgentValidateRequest,
  AgentRejectRequest,
  AgentHistoryItem,
  PagedAuditEvents,
  DossierDetail,
  DocumentResponse
} from '../models/agent.models';
import { AgentDocumentReviewRequest } from '../models/agent.models';

/**
 * Service Angular pour l'espace agent administratif.
 * Consomme les endpoints /agent/** sécurisés côté backend.
 * Réf. CDC § 8.2.
 */
@Injectable({ providedIn: 'root' })
export class AgentService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/agent`;

  /**
   * GET /agent/queue
   * File d'attente des dossiers à traiter pour l'organisme de l'agent.
   */
  getQueue(): Observable<AgentQueueItem[]> {
    return this.http.get<AgentQueueItem[]>(`${this.base}/queue`);
  }

  takeInCharge(dossierId: string): Observable<AgentQueueItem> {
    return this.http.post<AgentQueueItem>(`${this.base}/dossiers/${dossierId}/prise-en-charge`, {});
  }

  releaseClaim(dossierId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/dossiers/${dossierId}/claim`);
  }

  /**
   * GET /agent/dossiers/{id}
   * Détail complet d'un dossier (étapes, informations, progression).
   */
  getDossierDetail(dossierId: string): Observable<DossierDetail> {
    return this.http.get<DossierDetail>(`${this.base}/dossiers/${dossierId}`);
  }

  /**
   * GET /agent/dossiers/{id}/documents
   * Liste des documents déposés par l'entrepreneur pour un dossier.
   */
  getDocuments(dossierId: string): Observable<DocumentResponse[]> {
    return this.http.get<DocumentResponse[]>(
      `${this.base}/dossiers/${dossierId}/documents`
    );
  }

  reviewDocument(dossierId: string, documentId: string, request: AgentDocumentReviewRequest): Observable<void> {
    return this.http.put<void>(
      `${this.base}/dossiers/${dossierId}/documents/${documentId}/review`,
      request
    );
  }

  /**
   * Téléchargement d'un document (réutilise l'endpoint public /dossiers/{id}/documents/{docId}/content).
   * Le backend autorise les agents à accéder à ce contenu via l'authentification JWT.
   */
  downloadDocument(dossierId: string, docId: string): Observable<Blob> {
    return this.http.get(
      `${this.base}/dossiers/${dossierId}/documents/${docId}/content`,
      { responseType: 'blob' }
    );
  }

  uploadDocument(dossierId: string, file: File, documentTypeCode: string): Observable<DocumentResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('documentTypeCode', documentTypeCode);
    return this.http.post<DocumentResponse>(
      `${this.base}/dossiers/${dossierId}/documents`,
      formData
    );
  }

  /**
   * POST /agent/dossiers/{id}/validate
   * Valide l'étape courante (RG7 — passage automatique à l'étape suivante).
   */
  validateStep(dossierId: string, request?: AgentValidateRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/dossiers/${dossierId}/valider`,
      request ?? {}
    );
  }

  /**
   * POST /agent/dossiers/{id}/reject
   * Rejette l'étape courante avec motif obligatoire (RG8).
   */
  rejectStep(dossierId: string, request: AgentRejectRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/dossiers/${dossierId}/rejeter`,
      request
    );
  }

  /**
   * GET /agent/history
   * Historique des étapes validées ou rejetées par l'agent connecté.
   */
  getHistory(): Observable<AgentHistoryItem[]> {
    return this.http.get<AgentHistoryItem[]>(`${this.base}/history`);
  }

  getHistoricalDossier(dossierId: string): Observable<DossierDetail> {
    return this.http.get<DossierDetail>(`${this.base}/history/${dossierId}`);
  }

  getHistoricalDocuments(dossierId: string): Observable<DocumentResponse[]> {
    return this.http.get<DocumentResponse[]>(`${this.base}/history/${dossierId}/documents`);
  }

  getHistoricalAuditEvents(dossierId: string): Observable<PagedAuditEvents> {
    return this.http.get<PagedAuditEvents>(`${this.base}/history/${dossierId}/audit-events`);
  }

  downloadHistoricalDocument(dossierId: string, documentId: string): Observable<Blob> {
    return this.http.get(`${this.base}/history/${dossierId}/documents/${documentId}/content`, {
      responseType: 'blob'
    });
  }
}
