export interface AuthUser {
  id: string;
  email: string;
  role: 'USER' | 'ADMIN';
}

export type AuthStatus = 'INITIALIZING' | 'AUTHENTICATED' | 'UNAUTHENTICATED';

export interface AuthState {
  status: AuthStatus;
  accessToken: string | null;
  tokenExpiresAt: number | null; // Unix ms
  user: AuthUser | null;
}
