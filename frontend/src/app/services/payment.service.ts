import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Payment, PaymentConfirmation } from '../models/entrepreneur.models';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/dossiers`;

  getPayments(dossierId: string): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.baseUrl}/${encodeURIComponent(dossierId)}/payments`);
  }

  initiate(dossierId: string, stepTrackingId: string, amountMad?: number | null): Observable<Payment> {
    return this.http.post<Payment>(`${this.baseUrl}/${encodeURIComponent(dossierId)}/payments`, {
      stepTrackingId,
      amountMad: amountMad ?? undefined
    });
  }

  confirm(dossierId: string, paymentId: string, payload: PaymentConfirmation): Observable<Payment> {
    return this.http.post<Payment>(
      `${this.baseUrl}/${encodeURIComponent(dossierId)}/payments/${encodeURIComponent(paymentId)}/confirm`,
      payload
    );
  }

  downloadReceipt(dossierId: string, paymentId: string): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/${encodeURIComponent(dossierId)}/payments/${encodeURIComponent(paymentId)}/receipt`,
      { responseType: 'blob' }
    );
  }
}
