import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { filter, map, take } from 'rxjs/operators';
import { AuthStateService } from '../services/auth-state.service';
import { isSafeInternalUrl } from '../utils/safe-url.util';

export const authGuard: CanActivateFn = (route, state) => {
  const authState = inject(AuthStateService);
  const router = inject(Router);

  return authState.state$.pipe(
    filter(s => s.status !== 'INITIALIZING'),
    take(1),
    map(s => {
      if (s.status === 'AUTHENTICATED') {
        return true;
      }
      
      // Default returnUrl logic for unauthenticated users accessing protected routes
      const returnUrl = state.url;
      const safeReturnUrl = isSafeInternalUrl(returnUrl) ? returnUrl : '/applications';
      return router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: safeReturnUrl } });
    })
  );
};
