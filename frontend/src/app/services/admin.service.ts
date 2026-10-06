import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AdminDashboard,
  PagedDossiers,
  PagedAgents,
  AgentAdminItem,
  CreateAgentPayload,
  OrganismeItem,
  OrganismeCreatePayload,
  OrganismeUpdatePayload
} from '../models/admin.models';

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/admin`;

  getDashboard(period?: string, region?: string): Observable<AdminDashboard> {
    let params = new HttpParams();
    if (period) params = params.set('period', period);
    if (region && region !== 'Toutes les régions') params = params.set('region', region);

    return this.http.get<AdminDashboard>(`${this.baseUrl}/dashboard`, { params });
  }

  getDossiers(
    search?: string,
    status?: string,
    legalForm?: string,
    region?: string,
    currentStep?: string,
    page = 0,
    size = 10
  ): Observable<PagedDossiers> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (search && search.trim()) params = params.set('search', search.trim());
    if (status && status !== 'ALL' && status !== 'Tous les statuts') params = params.set('status', status);
    if (legalForm && legalForm !== 'ALL' && legalForm !== 'Toutes les formes') params = params.set('legalForm', legalForm);
    if (region && region !== 'ALL' && region !== 'Toutes les régions') params = params.set('region', region);
    if (currentStep && currentStep !== 'ALL' && currentStep !== 'Toutes les étapes') params = params.set('currentStep', currentStep);

    return this.http.get<PagedDossiers>(`${this.baseUrl}/dossiers`, { params });
  }

  exportDossiersCsv(
    search?: string,
    status?: string,
    legalForm?: string,
    region?: string
  ): Observable<Blob> {
    let params = new HttpParams();
    if (search && search.trim()) params = params.set('search', search.trim());
    if (status && status !== 'ALL' && status !== 'Tous les statuts') params = params.set('status', status);
    if (legalForm && legalForm !== 'ALL' && legalForm !== 'Toutes les formes') params = params.set('legalForm', legalForm);
    if (region && region !== 'ALL' && region !== 'Toutes les régions') params = params.set('region', region);

    return this.http.get(`${this.baseUrl}/dossiers/export`, {
      params,
      responseType: 'blob'
    });
  }

  getAgents(
    search?: string,
    organization?: string,
    active?: boolean | null,
    page = 0,
    size = 10
  ): Observable<PagedAgents> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (search && search.trim()) params = params.set('search', search.trim());
    if (organization && organization !== 'ALL' && organization !== 'Tous les organismes') {
      params = params.set('organization', organization);
    }
    if (active !== null && active !== undefined) {
      params = params.set('active', active.toString());
    }

    return this.http.get<PagedAgents>(`${this.baseUrl}/agents`, { params });
  }

  createAgent(payload: CreateAgentPayload): Observable<AgentAdminItem> {
    return this.http.post<AgentAdminItem>(`${this.baseUrl}/agents`, payload);
  }

  updateAgentStatus(agentId: string, active: boolean): Observable<AgentAdminItem> {
    return this.http.patch<AgentAdminItem>(
      `${this.baseUrl}/agents/${encodeURIComponent(agentId)}/status`,
      { active },
      { headers: { 'Content-Type': 'application/json' } }
    );
  }

  // ===================== ORGANISMES =====================
  getOrganismes(): Observable<OrganismeItem[]> {
    return this.http.get<OrganismeItem[]>(`${this.baseUrl}/organizations`);
  }

  createOrganisme(payload: OrganismeCreatePayload): Observable<OrganismeItem> {
    return this.http.post<OrganismeItem>(`${this.baseUrl}/organizations`, payload);
  }

  updateOrganisme(code: string, payload: OrganismeUpdatePayload): Observable<OrganismeItem> {
    return this.http.patch<OrganismeItem>(`${this.baseUrl}/organizations/${encodeURIComponent(code)}`, payload);
  }
}

