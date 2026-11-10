import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { NotificationItem, PagedNotifications } from '../models/entrepreneur.models';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/notifications`;

  getNotifications(unreadOnly?: boolean, page = 0, size = 15): Observable<PagedNotifications> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (unreadOnly !== undefined) params = params.set('unreadOnly', unreadOnly);
    return this.http.get<PagedNotifications>(this.baseUrl, { params });
  }

  markAsRead(id: string): Observable<NotificationItem> {
    return this.http.patch<NotificationItem>(`${this.baseUrl}/${encodeURIComponent(id)}/read`, {});
  }

  markAllAsRead(): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/read-all`, {});
  }
}
