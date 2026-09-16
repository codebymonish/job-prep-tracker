import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthStateService } from './auth-state.service';
import { LoginRequest, LoginResponse, RefreshResponse, RegisterRequest, RegisterResponse } from '../models/auth.dto';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private authState = inject(AuthStateService);
  private apiUrl = `${environment.apiUrl}/auth`;

  public initializeAuth(): Observable<boolean> {
    return this.refresh().pipe(
      map(() => true),
      catchError(() => {
        this.authState.setUnauthenticated();
        return of(false);
      })
    );
  }

  public register(req: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.apiUrl}/register`, req);
  }

  public verifyEmail(token: string): Observable<void> {
    return this.http.get<void>(`${this.apiUrl}/verify`, { params: { token } });
  }

  public login(req: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, req, {
      withCredentials: true
    }).pipe(
      tap((res) => {
        this.authState.setAuthenticated(res.accessToken);
      })
    );
  }

  public refresh(): Observable<string> {
    return this.http.post<RefreshResponse>(`${this.apiUrl}/refresh`, {}, {
      withCredentials: true,
      headers: {
        'X-Requested-With': 'XMLHttpRequest'
      }
    }).pipe(
      tap((res) => {
        this.authState.setAuthenticated(res.accessToken);
      }),
      map(res => res.accessToken),
      catchError(err => {
        this.authState.setUnauthenticated();
        throw err;
      })
    );
  }

  public requestPasswordReset(email: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/password-reset-request`, { email });
  }

  public resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/password-reset`, { token, newPassword });
  }

  public logout(): Observable<void> {
    this.authState.setUnauthenticated();
    return this.http.post<void>(`${this.apiUrl}/logout`, {}, {
      withCredentials: true
    }).pipe(
      catchError(() => of(void 0))
    );
  }
}
