import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthStateService } from '../services/auth-state.service';
import { AuthService } from '../services/auth.service';
import { AuthRefreshCoordinatorService } from './auth-refresh-coordinator.service';

const HAS_RETRIED = new HttpContextToken<boolean>(() => false);

const AUTH_ENDPOINTS = [
  '/api/v1/auth/login',
  '/api/v1/auth/register',
  '/api/v1/auth/verify',
  '/api/v1/auth/password-reset-request',
  '/api/v1/auth/password-reset',
  '/api/v1/auth/logout',
];
const REFRESH_ENDPOINT = '/api/v1/auth/refresh';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authState = inject(AuthStateService);
  const authService = inject(AuthService);
  const coordinator = inject(AuthRefreshCoordinatorService);
  const router = inject(Router);

  // 1. Check exclusions
  const isAuthEndpoint = AUTH_ENDPOINTS.some(path => req.url.includes(path));
  const isRefreshEndpoint = req.url.includes(REFRESH_ENDPOINT);

  let modifiedReq = req;
  
  // 2. Attach Bearer token if not excluded and token exists
  if (!isAuthEndpoint && !isRefreshEndpoint) {
    const token = authState.snapshot.accessToken;
    if (token) {
      modifiedReq = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }
  }

  return next(modifiedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // If it's a refresh call returning 401, don't loop
        if (isRefreshEndpoint) {
          return throwError(() => error);
        }

        // If it's a public auth endpoint returning 401 (e.g. login failed), don't trigger refresh
        if (isAuthEndpoint) {
          return throwError(() => error);
        }

        // If we already retried this request once, don't loop
        if (modifiedReq.context.get(HAS_RETRIED)) {
          return throwError(() => error);
        }

        // Trigger single-flight refresh
        const sharedRefresh$ = coordinator.getOrCreateRefresh(() => authService.refresh());

        return sharedRefresh$.pipe(
          switchMap(newToken => {
            // Check if logout fired while refresh was in flight
            if (authState.snapshot.status !== 'AUTHENTICATED') {
              return throwError(() => new Error('Session invalidated during refresh'));
            }

            // Clone request with new token and set retry flag
            const retryReq = req.clone({
              setHeaders: { Authorization: `Bearer ${newToken}` },
              context: req.context.set(HAS_RETRIED, true)
            });
            
            return next(retryReq);
          }),
          catchError(refreshErr => {
            // Navigate to login
            // Note: authService.refresh() already calls authState.setUnauthenticated() on its own error path.
            // We use standard router navigation.
            router.navigate(['/auth/login'], { queryParams: { returnUrl: getSafeReturnUrl(router) } });
            return throwError(() => refreshErr);
          })
        );
      }

      return throwError(() => error);
    })
  );
};

function getSafeReturnUrl(router: Router): string {
  // Use Angular router's current URL, which only returns internal paths
  const current = router.routerState.snapshot.url;
  // If it's already the login page or a public auth page, default to /applications
  if (current.includes('/auth/')) {
    return '/applications';
  }
  return current;
}
