import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DocumentItem } from '../models/entrepreneur.models';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/dossiers`;

  upload(dossierId: string, documentTypeCode: string, file: File, note?: string): Observable<DocumentItem> {
    const payload = new FormData();
    payload.append('file', file);
    payload.append('documentTypeCode', documentTypeCode);
    if (note?.trim()) payload.append('note', note.trim());
    return this.http.post<DocumentItem>(`${this.baseUrl}/${encodeURIComponent(dossierId)}/documents`, payload);
  }

  delete(dossierId: string, documentId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${encodeURIComponent(dossierId)}/documents/${encodeURIComponent(documentId)}`);
  }

  download(dossierId: string, documentId: string): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/${encodeURIComponent(dossierId)}/documents/${encodeURIComponent(documentId)}/content`,
      { responseType: 'blob' }
    );
  }

  generate(dossierId: string, type: 'STATUTS' | 'FORMULAIRE_UNIQUE'): Observable<DocumentItem> {
    return this.http.post<DocumentItem>(
      `${this.baseUrl}/${encodeURIComponent(dossierId)}/generated-documents/${type}`,
      {}
    );
  }
}
