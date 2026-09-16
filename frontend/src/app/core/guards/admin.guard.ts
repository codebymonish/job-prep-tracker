import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { filter, map, take } from 'rxjs/operators';
import { AuthStateService } from '../services/auth-state.service';

export const adminGuard: CanActivateFn = (route, state) => {
  const authState = inject(AuthStateService);
  const router = inject(Router);

  return authState.state$.pipe(
    filter(s => s.status !== 'INITIALIZING'),
    take(1),
    map(s => {
      if (s.status === 'AUTHENTICATED') {
        if (s.user?.role === 'ADMIN') {
          return true;
        }
        return router.createUrlTree(['/applications']);
      }
      
      const returnUrl = state.url;
      return router.createUrlTree(['/auth/login'], { queryParams: { returnUrl } });
    })
  );
};
