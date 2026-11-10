import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ChecklistItem,
  DossierDetail,
  DossierPayload,
  DossierStatus,
  PagedAuditEvents,
  PagedDossiers,
  StepTracking,
  SubmissionResult
} from '../models/entrepreneur.models';

@Injectable({ providedIn: 'root' })
export class DossierService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/dossiers`;

  getMyDossiers(status?: DossierStatus | 'ALL', page = 0, size = 10): Observable<PagedDossiers> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status && status !== 'ALL') params = params.set('status', status);
    return this.http.get<PagedDossiers>(this.baseUrl, { params });
  }

  getDossier(id: string): Observable<DossierDetail> {
    return this.http.get<DossierDetail>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }

  createDossier(payload: DossierPayload): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(this.baseUrl, payload);
  }

  updateDossier(id: string, payload: Partial<DossierPayload>): Observable<DossierDetail> {
    return this.http.put<DossierDetail>(`${this.baseUrl}/${encodeURIComponent(id)}`, payload);
  }

  getChecklist(id: string): Observable<ChecklistItem[]> {
    return this.http.get<ChecklistItem[]>(`${this.baseUrl}/${encodeURIComponent(id)}/checklist`);
  }

  getSteps(id: string): Observable<StepTracking[]> {
    return this.http.get<StepTracking[]>(`${this.baseUrl}/${encodeURIComponent(id)}/steps`);
  }

  getAuditEvents(id: string, page = 0, size = 20): Observable<PagedAuditEvents> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedAuditEvents>(`${this.baseUrl}/${encodeURIComponent(id)}/audit-events`, { params });
  }

  submitDossier(id: string): Observable<SubmissionResult> {
    return this.http.post<SubmissionResult>(`${this.baseUrl}/${encodeURIComponent(id)}/submit`, {});
  }
}
