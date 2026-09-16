import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { AuthState, AuthUser } from '../models/auth-state.model';

@Injectable({
  providedIn: 'root'
})
export class AuthStateService {
  private initialState: AuthState = {
    status: 'INITIALIZING',
    accessToken: null,
    tokenExpiresAt: null,
    user: null
  };

  private state = new BehaviorSubject<AuthState>(this.initialState);
  
  public state$ = this.state.asObservable();

  public get snapshot(): AuthState {
    return this.state.value;
  }

  public setAuthenticated(accessToken: string): void {
    const parsed = this.decodeJwt(accessToken);
    if (!parsed) {
      this.setUnauthenticated();
      return;
    }
    
    // JWT exp is in seconds
    const expiresAt = parsed.exp ? parsed.exp * 1000 : null;

    this.state.next({
      status: 'AUTHENTICATED',
      accessToken,
      tokenExpiresAt: expiresAt,
      user: parsed.user
    });
  }

  public setUnauthenticated(): void {
    this.state.next({
      status: 'UNAUTHENTICATED',
      accessToken: null,
      tokenExpiresAt: null,
      user: null
    });
  }


  private decodeJwt(token: string): { user: AuthUser, exp: number | null } | null {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      
      const payload = parts[1];
      // Pad string with '=' to make it a multiple of 4
      const paddedPayload = payload.padEnd(payload.length + (4 - payload.length % 4) % 4, '=');
      
      // Base64Url to Base64
      const base64 = paddedPayload.replace(/-/g, '+').replace(/_/g, '/');
      const decodedStr = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      
      const claims = JSON.parse(decodedStr);
      
      return {
        user: {
          id: claims.sub,
          email: claims.email,
          role: claims.role || 'USER'
        },
        exp: claims.exp || null
      };
    } catch (e) {
      return null;
    }
  }
}
