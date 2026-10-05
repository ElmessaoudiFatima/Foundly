import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError, of } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AuthSession,
  LoginRequest,
  RegisterRequest,
  User
} from '../models/auth.models';

const TOKEN_KEY = 'foundly_access_token';
const REFRESH_TOKEN_KEY = 'foundly_refresh_token';
const USER_KEY = 'foundly_user';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly currentUserSignal = signal<User | null>(this.loadUserFromStorage());

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.currentUserSignal());

  register(payload: RegisterRequest): Observable<AuthSession> {
    return this.http
      .post<AuthSession>(`${environment.apiUrl}/auth/register`, payload)
      .pipe(
        tap((session) => this.handleAuthSuccess(session))
      );
  }

  login(payload: LoginRequest): Observable<AuthSession> {
    return this.http
      .post<AuthSession>(`${environment.apiUrl}/auth/login`, payload)
      .pipe(
        tap((session) => this.handleAuthSuccess(session))
      );
  }

  refresh(): Observable<AuthSession> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      this.clearSession();
      return throwError(() => new Error('Aucun jeton de rafraîchissement disponible.'));
    }

    return this.http
      .post<AuthSession>(`${environment.apiUrl}/auth/refresh`, { refreshToken })
      .pipe(
        tap((session) => this.handleAuthSuccess(session)),
        catchError((err) => {
          this.clearSession();
          return throwError(() => err);
        })
      );
  }

  logout(): Observable<void> {
    const token = this.getAccessToken();
    if (!token) {
      this.clearSession();
      this.router.navigate(['/login']);
      return of(void 0);
    }

    return this.http
      .post<void>(`${environment.apiUrl}/auth/logout`, {})
      .pipe(
        tap(() => {
          this.clearSession();
          this.router.navigate(['/login']);
        }),
        catchError(() => {
          // Even if server-side revocation fails, clear client session
          this.clearSession();
          this.router.navigate(['/login']);
          return of(void 0);
        })
      );
  }

  getAccessToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  private handleAuthSuccess(session: AuthSession): void {
    localStorage.setItem(TOKEN_KEY, session.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(session.user));
    this.currentUserSignal.set(session.user);
  }

  clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUserSignal.set(null);
  }

  private loadUserFromStorage(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  }
}
